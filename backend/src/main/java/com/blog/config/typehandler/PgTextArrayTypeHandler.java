package com.blog.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL {@code text[]} ↔ Java 逗号串 的类型桥。
 * <p>
 * V1 把「情绪标签」从逗号串改成了 {@code text[]}（{@code memory_note.emotions} /
 * {@code memory_emotion.aliases}），但业务层到处是 {@code split(",")} / {@code String.join(",")}，
 * 全量改成 {@code List<String>} 会牵动十来个文件和前端契约。
 * <p>
 * 折中办法就是这一层：**库内是数组，Java 里仍是逗号串**，边界处由本 TypeHandler 双向转换。
 * 只管这两个字段，因为它是通过 {@code @TableField(typeHandler = ...)} 显式绑定到字段上的，
 * 不会像 {@code @MappedTypes(String.class)} 那样污染所有 String 列。
 * <p>
 * 使用前提：实体上必须 {@code @TableName(value = "xxx", autoResultMap = true)}，
 * 否则 MP 走自动映射、不认字段上的 typeHandler，读出来的会是 {@code {a,b}} 这种原始数组文本。
 */
public class PgTextArrayTypeHandler extends BaseTypeHandler<String> {

    private static final String DELIMITER = ",";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        // createArrayOf 是 pgjdbc 的标准用法；空串会变成 '{}'::text[]
        ps.setArray(i, ps.getConnection().createArrayOf("text", split(parameter)));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return join(rs.getArray(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return join(rs.getArray(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return join(cs.getArray(columnIndex));
    }

    /** text[] → 逗号串；NULL → null，空数组 → "" */
    private String join(Array array) throws SQLException {
        if (array == null) return null;
        try {
            Object raw = array.getArray();
            if (raw instanceof Object[] values) {
                List<String> out = new ArrayList<>(values.length);
                for (Object v : values) {
                    if (v != null && !v.toString().isBlank()) out.add(v.toString().trim());
                }
                return String.join(DELIMITER, out);
            }
            return raw == null ? null : raw.toString();
        } finally {
            // PgArray 由驱动管理，free() 可安全释放；有些驱动实现会抛 UnsupportedOperationException
            try {
                array.free();
            } catch (Exception ignore) {
                // 忽略：PG 驱动释放失败不影响取值
            }
        }
    }

    /** 逗号串 → text[]（去空白、去空项） */
    private String[] split(String value) {
        if (value == null || value.isBlank()) return new String[0];
        List<String> out = new ArrayList<>();
        for (String part : value.split("[,，]")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.toArray(new String[0]);
    }
}
