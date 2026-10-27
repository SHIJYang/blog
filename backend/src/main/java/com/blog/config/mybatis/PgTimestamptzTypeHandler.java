package com.blog.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * {@code timestamptz} ↔ {@code LocalDateTime} 的映射。
 * <p>
 * 为什么必须自己写：pgjdbc 对 {@code timestamptz} 列调用
 * {@code rs.getObject(col, LocalDateTime.class)} 会直接抛
 * {@code Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime}，
 * 而 MyBatis 自带的 {@code LocalDateTimeTypeHandler} 正是这么调的。
 * 表现为：**空表一切正常，一旦查出数据就 500**（登录接口最先炸，因为 loadUserByUsername 查 sys_user）。
 * <p>
 * 读：先取 {@link OffsetDateTime}（它带绝对时刻，无歧义），再换到写入时用的同一个时区取墙上时间。
 * 写：{@code setObject(LocalDateTime)} 会以 timestamp(无时区) 发出，由会话时区
 * （见 application.yml 的 {@code hikari.connection-init-sql = SET TIME ZONE 'Asia/Shanghai'}）
 * 折算成 timestamptz，两端因此对齐，且不依赖 JVM 默认时区。
 * <p>
 * 通过 {@code mybatis-plus.type-handlers-package} 全局注册——**这个包里的 handler 都是全局生效的**，
 * 新增时务必确认没有抢注 {@code String} 之类的高频类型（{@code PgTextArrayTypeHandler} 因此不在本包）。
 */
public class PgTimestamptzTypeHandler extends BaseTypeHandler<LocalDateTime> {

    /** 必须与 application.yml 里的会话时区一致 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return convert(rs.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return convert(rs.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return convert(cs.getObject(columnIndex, OffsetDateTime.class));
    }

    private LocalDateTime convert(OffsetDateTime odt) {
        return odt == null ? null : odt.atZoneSameInstant(ZONE).toLocalDateTime();
    }
}
