package com.blog.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * update_time 自动回填。
 * <p>
 * MySQL 时代靠 {@code ON UPDATE CURRENT_TIMESTAMP} 由库兜底，PG 没有这个能力
 * （V1 的表头注释里把这条写成了迁移代价）。所以改在应用层统一填：每次 insert/update
 * 由 MP 调这里写值，比数据库触发器可控、也能单测。
 * <p>
 * 只有标了 {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 的字段会生效。
 * {@code strict*} 变体的语义是"字段为空才填"，因此业务代码显式 set 过的 updateTime 不会被覆盖。
 */
@Component
public class UpdateTimeMetaObjectHandler implements MetaObjectHandler {

    private static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
