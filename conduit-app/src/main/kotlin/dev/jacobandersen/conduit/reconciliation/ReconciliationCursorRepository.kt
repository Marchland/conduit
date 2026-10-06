package dev.jacobandersen.conduit.reconciliation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReconciliationCursorRepository : JpaRepository<ReconciliationCursorEntity, String>
