package com.amx.grocerystore.inventory.infrastructure;

import com.amx.grocerystore.inventory.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
}
