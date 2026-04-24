-- =====================================================================
-- ALEX SISTEMAS - SaaS Database for Grocery Stores
-- Engine:   PostgreSQL 14+
-- Scope:    Tequila, Jalisco (multi-tenant in a single database)
-- Philosophy: Minimal friction for shopkeepers, maximum automation in DB
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- for gen_random_uuid()

-- =====================================================================
-- MODULE 1: ADMINISTRATIVE (YOUR SAAS BUSINESS)
-- =====================================================================

-- Subscription plan catalog (one for now, extensible later)
CREATE TABLE subscription_plans
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    name        VARCHAR(50)    NOT NULL,
    monthly_fee NUMERIC(10, 2) NOT NULL DEFAULT 499.00,
    setup_fee   NUMERIC(10, 2) NOT NULL DEFAULT 1490.00,
    description TEXT,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Stores = SaaS clients (tenants)
CREATE TABLE stores
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    code       VARCHAR(30)  NOT NULL UNIQUE
        CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'),
    name       VARCHAR(150) NOT NULL,
    owner_name VARCHAR(150),
    whatsapp   VARCHAR(20),
    address    TEXT, -- free text, no city/state split
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Links a store with a plan and tracks its billing cycle
CREATE TABLE subscriptions
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    store_id          UUID        NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    plan_id           UUID        NOT NULL REFERENCES subscription_plans (id),
    start_date        DATE        NOT NULL DEFAULT CURRENT_DATE,
    next_billing_date DATE        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'OVERDUE', 'CANCELED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- SaaS payment history (setup fees + monthly fees)
CREATE TABLE saas_payments
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    store_id       UUID           NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    amount         NUMERIC(10, 2) NOT NULL,
    concept        VARCHAR(30)    NOT NULL
        CHECK (concept IN ('SETUP_FEE', 'MONTHLY_FEE')),
    payment_method VARCHAR(20)
        CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'TRANSFER', 'CARD')),
    period_start   DATE, -- for monthly fees
    period_end     DATE,
    paid_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    notes          TEXT
);

-- SaaS platform administrators (no store affiliation)
CREATE TABLE admin_users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'SUPER_ADMIN'
        CHECK (role IN ('SUPER_ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);


-- =====================================================================
-- MODULE 2: STORE OPERATIONS
-- =====================================================================

-- POS users (owner + cashiers). No email required.
CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    store_id      UUID         NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    full_name     VARCHAR(150) NOT NULL,
    username      VARCHAR(50)  NOT NULL, -- local login
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CASHIER' CHECK (role IN ('OWNER', 'CASHIER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (store_id, username)
);

-- Products: ONLY the essentials for fast entry.
-- Required: name and sale_price. Everything else is optional.
CREATE TABLE products
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    store_id        UUID           NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    barcode         VARCHAR(50),
    name            VARCHAR(150)   NOT NULL,
    purchase_cost   NUMERIC(10, 2),
    sale_price      NUMERIC(10, 2) NOT NULL CHECK (sale_price >= 0),
    current_stock   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    minimum_stock   NUMERIC(10, 2) NOT NULL DEFAULT 5,
    unit_of_measure VARCHAR(10)    NOT NULL DEFAULT 'PIECE' CHECK (unit_of_measure IN ('PIECE', 'KILO')),
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (store_id, barcode)
);


-- =====================================================================
-- MODULE 3: SALES AND CREDIT (FIADOS)
-- =====================================================================

CREATE TABLE customers
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    store_id            UUID           NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    full_name           VARCHAR(150)   NOT NULL,
    phone               VARCHAR(20),
    outstanding_balance NUMERIC(10, 2) NOT NULL DEFAULT 0, -- current debt
    credit_limit        NUMERIC(10, 2) NOT NULL DEFAULT 1000,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Sales: supports mixed payment (cash + credit in the same transaction).
-- Cancellation details live in their own table (sale_cancellations).
CREATE TABLE sales
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    store_id        UUID           NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    user_id         UUID           REFERENCES users (id) ON DELETE SET NULL,
    customer_id     UUID           REFERENCES customers (id) ON DELETE SET NULL, -- NULL = walk-in
    total_amount    NUMERIC(10, 2) NOT NULL,
    amount_paid     NUMERIC(10, 2) NOT NULL DEFAULT 0,                           -- paid at the moment
    amount_credited NUMERIC(10, 2) NOT NULL DEFAULT 0,                           -- left on credit
    payment_method  VARCHAR(20)    NOT NULL
        CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER', 'CREDIT', 'MIXED')),
    status          VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED'
        CHECK (status IN ('COMPLETED', 'CANCELED')),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    -- Integrity: paid + credited must equal the total
    CONSTRAINT chk_sale_balances CHECK (
        amount_paid + amount_credited = total_amount
            AND amount_paid >= 0
            AND amount_credited >= 0
        ),
    -- If anything is on credit, a customer is required
    CONSTRAINT chk_credit_needs_customer CHECK (
        amount_credited = 0 OR customer_id IS NOT NULL
        )
);

CREATE TABLE sale_items
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id    UUID           NOT NULL REFERENCES sales (id) ON DELETE RESTRICT,
    product_id UUID           NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    quantity   NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL -- historical snapshot
);

-- Customer payments (partial or full repayment of their debt)
CREATE TABLE customer_payments
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    store_id    UUID           NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    customer_id UUID           NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    user_id     UUID           REFERENCES users (id) ON DELETE SET NULL,
    amount      NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Sale cancellations: a separate row only exists when a sale is canceled.
-- 1-to-1 with sales (via UNIQUE on sale_id).
CREATE TABLE sale_cancellations
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    sale_id     UUID        NOT NULL UNIQUE REFERENCES sales (id) ON DELETE RESTRICT,
    canceled_by UUID        REFERENCES users (id) ON DELETE SET NULL,
    reason      VARCHAR(30) NOT NULL
        CHECK (reason IN ('CASHIER_ERROR', 'CUSTOMER_REGRET', 'WRONG_PRODUCT', 'OTHER')),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- =====================================================================
-- MODULE 4: INVENTORY AUDIT AND NOTIFICATIONS
-- =====================================================================

-- Trail of every stock change (sale, restock, loss, adjustment)
CREATE TABLE inventory_movements
(
    id         UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    store_id   UUID           NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    product_id UUID           NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    quantity   NUMERIC(10, 2) NOT NULL, -- + in, - out
    reason     VARCHAR(20)    NOT NULL
        CHECK (reason IN ('SALE', 'RESTOCK', 'LOSS', 'ADJUSTMENT', 'CANCELLATION')),
    sale_id    UUID           REFERENCES sales (id) ON DELETE SET NULL,
    notes      TEXT,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Alerts for the owner (low stock, overdue credit, etc.)
CREATE TABLE notifications
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    store_id     UUID         NOT NULL REFERENCES stores (id) ON DELETE RESTRICT,
    type         VARCHAR(30)  NOT NULL
        CHECK (type IN ('LOW_STOCK', 'OUT_OF_STOCK', 'OVERDUE_CREDIT')),
    title        VARCHAR(200) NOT NULL,
    message      TEXT,
    reference_id UUID,        -- product_id, customer_id, etc.
    read_at      TIMESTAMPTZ, -- NULL = unread
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);


-- =====================================================================
-- INDEXES
-- =====================================================================

CREATE INDEX idx_products_store ON products (store_id) WHERE active;
CREATE INDEX idx_products_low_stock ON products (store_id) WHERE active AND current_stock <= minimum_stock;
-- barcode lookup is served by the UNIQUE (store_id, barcode) constraint above
CREATE INDEX idx_sales_store_date ON sales (store_id, created_at DESC);
CREATE INDEX idx_sales_completed ON sales (store_id, created_at DESC) WHERE status = 'COMPLETED';
CREATE INDEX idx_sales_canceled ON sales (store_id, created_at DESC) WHERE status = 'CANCELED';
CREATE INDEX idx_cancellations_date ON sale_cancellations (created_at DESC);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);
CREATE INDEX idx_payments_customer ON customer_payments (customer_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (store_id) WHERE read_at IS NULL;
CREATE INDEX idx_subscriptions_billing ON subscriptions (next_billing_date) WHERE status = 'ACTIVE';

-- Enforce only one active subscription per store
CREATE UNIQUE INDEX idx_one_active_subscription
    ON subscriptions (store_id) WHERE status = 'ACTIVE';


-- =====================================================================
-- TRIGGERS (automatic logic so the app doesn't have to handle it)
-- =====================================================================

-- 1) On sale_items insert:
--    - decrement product stock
--    - log inventory movement
--    - raise notification if crossing minimum threshold
CREATE
    OR
    REPLACE FUNCTION fn_apply_sale_item() RETURNS TRIGGER AS
$$
DECLARE
    v_stock_before NUMERIC(10, 2);
    v_stock_after  NUMERIC(10, 2);
    v_min_stock    NUMERIC(10, 2);
    v_store_id     UUID;
    v_product_name TEXT;
BEGIN
    SELECT current_stock, minimum_stock, store_id, name
    INTO v_stock_before, v_min_stock, v_store_id, v_product_name
    FROM products
    WHERE id = NEW.product_id;

    v_stock_after := v_stock_before - NEW.quantity;

    UPDATE products
    SET current_stock = v_stock_after,
        updated_at    = NOW()
    WHERE id = NEW.product_id;

    INSERT INTO inventory_movements (store_id, product_id, quantity, reason, sale_id)
    VALUES (v_store_id, NEW.product_id, -NEW.quantity, 'SALE', NEW.sale_id);

-- Notify only when crossing the threshold (not every time it's already low)
    IF v_stock_before > v_min_stock AND v_stock_after <= v_min_stock THEN
        INSERT INTO notifications (store_id, type, title, message, reference_id)
        VALUES (v_store_id,
                CASE WHEN v_stock_after <= 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END,
                'Running low: ' || v_product_name,
                GREATEST(v_stock_after, 0) || ' left (minimum ' || v_min_stock || ')',
                NEW.product_id);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_apply_sale_item
    AFTER INSERT
    ON sale_items
    FOR EACH ROW
EXECUTE FUNCTION fn_apply_sale_item();


-- 2) On sale with credit: add the credited amount to customer's debt
CREATE
    OR
    REPLACE FUNCTION fn_register_credit() RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.amount_credited > 0 AND NEW.customer_id IS NOT NULL THEN
        UPDATE customers
        SET outstanding_balance = outstanding_balance + NEW.amount_credited
        WHERE id = NEW.customer_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_register_credit
    AFTER INSERT
    ON sales
    FOR EACH ROW
EXECUTE FUNCTION fn_register_credit();


-- 3) On customer payment: subtract from outstanding balance
CREATE
    OR
    REPLACE FUNCTION fn_apply_payment() RETURNS TRIGGER AS
$$
BEGIN
    UPDATE customers
    SET outstanding_balance = outstanding_balance - NEW.amount
    WHERE id = NEW.customer_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_apply_payment
    AFTER INSERT
    ON customer_payments
    FOR EACH ROW
EXECUTE FUNCTION fn_apply_payment();


-- 4) On sale cancellation (INSERT into sale_cancellations):
--    - mark the sale as CANCELED
--    - return items to stock + log inventory movements
--    - remove credited amount from customer's debt (if any)
CREATE
    OR
    REPLACE FUNCTION fn_cancel_sale() RETURNS TRIGGER AS
$$
DECLARE
    r_item RECORD;
    v_sale RECORD;
BEGIN
    -- Fetch the sale being canceled
    SELECT *
    INTO v_sale
    FROM sales
    WHERE id = NEW.sale_id;

    IF v_sale.status = 'CANCELED' THEN
        RAISE EXCEPTION 'Sale % is already canceled', NEW.sale_id;
    END IF;

    -- Mark the sale itself as canceled
    UPDATE sales
    SET status = 'CANCELED'
    WHERE id = NEW.sale_id;

-- Return each item's quantity to the product stock
    FOR r_item IN
        SELECT product_id, quantity
        FROM sale_items
        WHERE sale_id = NEW.sale_id
        LOOP
            UPDATE products
            SET current_stock = current_stock + r_item.quantity,
                updated_at    = NOW()
            WHERE id = r_item.product_id;

            INSERT INTO inventory_movements (store_id, product_id, quantity, reason, sale_id, notes)
            VALUES (v_sale.store_id, r_item.product_id, r_item.quantity, 'CANCELLATION', NEW.sale_id,
                    'Sale canceled: ' || NEW.reason);
        END LOOP;

    -- If the sale had credit, reverse the debt
    IF v_sale.amount_credited > 0 AND v_sale.customer_id IS NOT NULL THEN
        UPDATE customers
        SET outstanding_balance = outstanding_balance - v_sale.amount_credited
        WHERE id = v_sale.customer_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cancel_sale
    AFTER INSERT
    ON sale_cancellations
    FOR EACH ROW
EXECUTE FUNCTION fn_cancel_sale();


-- Block adding items to an already-canceled sale
CREATE
    OR
    REPLACE FUNCTION fn_block_items_on_canceled() RETURNS TRIGGER AS
$$
BEGIN
    IF
        (SELECT status FROM sales WHERE id = NEW.sale_id)
            = 'CANCELED' THEN
        RAISE EXCEPTION 'Cannot add items to a canceled sale';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_items_on_canceled
    BEFORE INSERT
    ON sale_items
    FOR EACH ROW
EXECUTE FUNCTION fn_block_items_on_canceled();


-- Prevent deleting a cancellation record (that would "undo" the cancellation)
CREATE
    OR
    REPLACE FUNCTION fn_block_cancellation_delete() RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'Cancellations cannot be deleted. They are permanent audit records.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_cancellation_delete
    BEFORE DELETE
    ON sale_cancellations
    FOR EACH ROW
EXECUTE FUNCTION fn_block_cancellation_delete();


-- =====================================================================
-- DASHBOARD VIEWS
-- =====================================================================

-- Products that need restocking
CREATE OR REPLACE VIEW v_products_to_restock AS
SELECT store_id, id AS product_id, name, current_stock, minimum_stock
FROM products
WHERE active
  AND current_stock <= minimum_stock
ORDER BY current_stock ASC;

-- Customers with outstanding debt
CREATE OR REPLACE VIEW v_customers_with_debt AS
SELECT store_id,
       id AS customer_id,
       full_name,
       phone,
       outstanding_balance,
       credit_limit
FROM customers
WHERE active
  AND outstanding_balance > 0
ORDER BY outstanding_balance DESC;

-- Sales breakdown for today (excludes canceled sales)
CREATE OR REPLACE VIEW v_sales_today AS
SELECT store_id,
       COUNT(*)             AS tickets,
       SUM(total_amount)    AS day_total,
       SUM(amount_paid)     AS day_collected,
       SUM(amount_credited) AS day_on_credit
FROM sales
WHERE created_at::date = CURRENT_DATE
  AND status = 'COMPLETED'
GROUP BY store_id;

-- Sale with payment breakdown (handy for receipts)
CREATE OR REPLACE VIEW v_sales_detail AS
SELECT s.id        AS sale_id,
       s.store_id,
       s.created_at,
       c.full_name AS customer,
       s.total_amount,
       s.amount_paid,
       s.amount_credited,
       s.status,
       CASE
           WHEN s.status = 'CANCELED' THEN 'CANCELED'
           WHEN s.amount_credited = 0 THEN 'PAID'
           WHEN s.amount_paid = 0 THEN 'FULL_CREDIT'
           ELSE 'PARTIAL_CREDIT'
           END     AS sale_type
FROM sales s
         LEFT JOIN customers c ON c.id = s.customer_id;

-- Canceled sales log (for the owner to review)
CREATE OR REPLACE VIEW v_canceled_sales AS
SELECT s.id          AS sale_id,
       s.store_id,
       s.created_at  AS sold_at,
       sc.created_at AS canceled_at,
       s.total_amount,
       c.full_name   AS customer,
       u.full_name   AS canceled_by,
       sc.reason     AS cancel_reason,
       sc.notes      AS cancel_notes
FROM sales s
         JOIN sale_cancellations sc ON sc.sale_id = s.id
         LEFT JOIN customers c ON c.id = s.customer_id
         LEFT JOIN users u ON u.id = sc.canceled_by
ORDER BY sc.created_at DESC;

-- Subscriptions due within the next 7 days (for YOU to collect)
CREATE OR REPLACE VIEW v_upcoming_billings AS
SELECT s.store_id,
       st.name AS store_name,
       st.whatsapp,
       s.next_billing_date,
       p.monthly_fee
FROM subscriptions s
         JOIN stores st ON st.id = s.store_id
         JOIN subscription_plans p ON p.id = s.plan_id
WHERE s.status = 'ACTIVE'
  AND s.next_billing_date <= CURRENT_DATE + INTERVAL '7 days'
ORDER BY s.next_billing_date;


-- =====================================================================
-- SEED DATA
-- =====================================================================

-- Standard plan (the only one for now)
INSERT INTO subscription_plans (id, name, monthly_fee, setup_fee, description)
VALUES ('00000000-0000-0000-0000-000000000001',
        'Standard Plan', 499.00, 1490.00,
        'Inventory, credit tracking and notifications from your phone');

-- Demo store
INSERT INTO stores (id, name, owner_name, whatsapp, address)
VALUES ('11111111-1111-1111-1111-111111111111',
        'Abarrotes Doña Lupe', 'Guadalupe Ramírez',
        '3741112222', 'Calle Hidalgo 45, Tequila');

-- Active subscription for the demo store
INSERT INTO subscriptions (store_id, plan_id, next_billing_date)
VALUES ('11111111-1111-1111-1111-111111111111',
        '00000000-0000-0000-0000-000000000001',
        CURRENT_DATE + INTERVAL '30 days');

-- Setup fee payment record
INSERT INTO saas_payments (store_id, amount, concept, payment_method)
VALUES ('11111111-1111-1111-1111-111111111111',
        1490.00, 'SETUP_FEE', 'CASH');