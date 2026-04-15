package com.code.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupplierAccountMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupplierAccountMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public SupplierAccountMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("suppliers")) {
            return;
        }

        migrateSupplierReferences("purchase_order");
        migrateSupplierReferences("purchase_request");
        dropForeignKeysReferencingSuppliers("purchase_order");
        dropForeignKeysReferencingSuppliers("purchase_request");
        clearInvalidSupplierReferences("purchase_order");
        clearInvalidSupplierReferences("purchase_request");
        jdbcTemplate.execute("DROP TABLE IF EXISTS `suppliers`");
        log.info("Completed supplier account migration: switched procurement supplier references to users table.");
    }

    private void migrateSupplierReferences(String tableName) {
        int migratedByEmail = jdbcTemplate.update(
                "UPDATE `" + tableName + "` target " +
                        "JOIN `suppliers` supplier ON target.`supplier_id` = supplier.`id` " +
                        "JOIN `users` user_account ON LOWER(user_account.`email`) = LOWER(supplier.`email`) " +
                        "SET target.`supplier_id` = user_account.`id` " +
                        "WHERE target.`supplier_id` IS NOT NULL"
        );
        int migratedByUsername = jdbcTemplate.update(
                "UPDATE `" + tableName + "` target " +
                        "JOIN `suppliers` supplier ON target.`supplier_id` = supplier.`id` " +
                        "JOIN `users` user_account ON LOWER(user_account.`username`) = LOWER(supplier.`code`) " +
                        "LEFT JOIN `users` existing_user ON existing_user.`id` = target.`supplier_id` " +
                        "SET target.`supplier_id` = user_account.`id` " +
                        "WHERE target.`supplier_id` IS NOT NULL AND existing_user.`id` IS NULL"
        );
        int migratedByName = jdbcTemplate.update(
                "UPDATE `" + tableName + "` target " +
                        "JOIN `suppliers` supplier ON target.`supplier_id` = supplier.`id` " +
                        "JOIN `users` user_account ON LOWER(user_account.`full_name`) = LOWER(supplier.`name`) " +
                        "LEFT JOIN `users` existing_user ON existing_user.`id` = target.`supplier_id` " +
                        "SET target.`supplier_id` = user_account.`id` " +
                        "WHERE target.`supplier_id` IS NOT NULL AND existing_user.`id` IS NULL"
        );
        if (migratedByEmail + migratedByUsername + migratedByName > 0) {
            log.info("Migrated {} supplier references in {} (email={}, username/code={}, fullName/name={}).",
                    migratedByEmail + migratedByUsername + migratedByName,
                    tableName,
                    migratedByEmail,
                    migratedByUsername,
                    migratedByName);
        }
    }

    private void clearInvalidSupplierReferences(String tableName) {
        int cleared = jdbcTemplate.update(
                "UPDATE `" + tableName + "` target " +
                        "LEFT JOIN `users` user_account ON target.`supplier_id` = user_account.`id` " +
                        "SET target.`supplier_id` = NULL " +
                        "WHERE target.`supplier_id` IS NOT NULL AND user_account.`id` IS NULL"
        );
        if (cleared > 0) {
            log.warn("Cleared {} dangling supplier references from {} because no matching supplier user account was found.", cleared, tableName);
        }
    }

    private void dropForeignKeysReferencingSuppliers(String tableName) {
        List<String> constraintNames = jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME " +
                        "FROM information_schema.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = ? " +
                        "AND COLUMN_NAME = 'supplier_id' " +
                        "AND REFERENCED_TABLE_NAME = 'suppliers'",
                String.class,
                tableName
        );

        for (String constraintName : constraintNames) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP FOREIGN KEY `" + constraintName + "`");
            log.info("Dropped foreign key {} on {}.supplier_id before removing suppliers table.", constraintName, tableName);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}

