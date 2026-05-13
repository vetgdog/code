package com.code.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/*
 * 供应商账号历史迁移任务。
 *
 * <p>该组件用于把旧模型中独立 `suppliers` 表上的供应商引用，迁移到当前统一的 `users` 账号体系上。
 * 这是一次典型的“启动时自修复”兼容策略：应用启动后先检测旧表是否存在，若存在则尝试迁移引用、清理悬挂外键并最终移除旧表，
 * 让采购流程逐步收敛到统一账号模型。</p>
 */
public class SupplierAccountMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupplierAccountMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public SupplierAccountMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 先做存在性探测，避免在新库或已迁移环境中无意义执行迁移 SQL。
        if (!tableExists("suppliers")) {
            return;
        }

        // 迁移顺序很关键：先改数据，再拆旧外键，最后删旧表，
        // 否则可能出现引用关系还在但源表已被删除的中间损坏状态。
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
        // 第一层优先按 email 匹配，因为邮箱通常是当前系统最稳定的登录标识。
        int migratedByEmail = jdbcTemplate.update(
                "UPDATE `" + tableName + "` target " +
                        "JOIN `suppliers` supplier ON target.`supplier_id` = supplier.`id` " +
                        "JOIN `users` user_account ON LOWER(user_account.`email`) = LOWER(supplier.`email`) " +
                        "SET target.`supplier_id` = user_account.`id` " +
                        "WHERE target.`supplier_id` IS NOT NULL"
        );

        // 第二层按 username/code 匹配，用于兼容早期“供应商编码即账号名”的历史数据。
        int migratedByUsername = jdbcTemplate.update(
                "UPDATE `" + tableName + "` target " +
                        "JOIN `suppliers` supplier ON target.`supplier_id` = supplier.`id` " +
                        "JOIN `users` user_account ON LOWER(user_account.`username`) = LOWER(supplier.`code`) " +
                        "LEFT JOIN `users` existing_user ON existing_user.`id` = target.`supplier_id` " +
                        "SET target.`supplier_id` = user_account.`id` " +
                        "WHERE target.`supplier_id` IS NOT NULL AND existing_user.`id` IS NULL"
        );

        // 第三层按 fullName/name 匹配，属于兜底兼容手段，准确度弱于 email，
        // 但能尽量减少迁移后仍残留的旧引用。
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
        // 对最终仍找不到匹配 user 的历史引用进行置空，而不是保留脏外键，
        // 这样后续业务至少可以显式识别“该采购记录没有有效供应商账号”，而不会在查询时出现隐藏异常。
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
        // 通过 information_schema 动态查询外键名，而不是写死约束名，
        // 能兼容不同环境中由 JPA/数据库生成的实际 FK 名称差异。
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
        // 直接查 information_schema 比 try/catch 执行表查询更可控，
        // 同时不会因为表不存在而污染日志为异常堆栈。
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}

