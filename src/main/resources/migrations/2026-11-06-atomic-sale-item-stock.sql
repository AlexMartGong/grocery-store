-- =====================================================================
-- Migration 2026-11-06: atomic stock decrement in fn_apply_sale_item
-- =====================================================================
-- The previous fn_apply_sale_item read current_stock with a plain SELECT and then wrote an absolute
-- value (SET current_stock = v_stock_after). Under READ COMMITTED two concurrent sales of the same
-- product both read the same stock and the second overwrote the first: silent oversell,
-- chk_positive_stock never fired, inventory_movements no longer summed to current_stock.
-- The new version locks the row (SELECT ... FOR NO KEY UPDATE) and decrements relatively.
--
-- Function body only: no table/column change, so ddl-auto=validate is unaffected and this can be
-- applied before or after deploying any app version. trg_apply_sale_item references the function by
-- name, so it picks up the new body without being recreated.
--
--   docker exec -i ventacore-db psql -U postgres -d grocerystore -v ON_ERROR_STOP=1 \
--     < src/main/resources/migrations/2026-11-06-atomic-sale-item-stock.sql
--
-- Idempotent (CREATE OR REPLACE) and transactional: safe to re-run.
-- Mirrors fn_apply_sale_item in script.sql (TRIGGERS, section 1).

BEGIN;

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
    WHERE id = NEW.product_id
        FOR NO KEY UPDATE;

    v_stock_after := v_stock_before - NEW.quantity;

    UPDATE products
    SET current_stock = current_stock - NEW.quantity,
        updated_at    = NOW()
    WHERE id = NEW.product_id;

    INSERT INTO inventory_movements (store_id, product_id, quantity, reason, sale_id)
    VALUES (v_store_id, NEW.product_id, -NEW.quantity, 'SALE', NEW.sale_id);

-- Notify only when crossing the threshold (not every time it's already low)
    IF v_stock_before > v_min_stock AND v_stock_after <= v_min_stock THEN
        INSERT INTO notifications (store_id, type, title, message, reference_id)
        VALUES (v_store_id,
                CASE WHEN v_stock_after <= 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END,
                CASE WHEN v_stock_after <= 0 THEN 'Agotado: ' ELSE 'Poco inventario: ' END || v_product_name,
                'Quedan ' || GREATEST(v_stock_after, 0) || ' (mínimo ' || v_min_stock || ')',
                NEW.product_id);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMIT;
