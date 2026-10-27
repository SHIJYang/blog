package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.blog.common.JsoupUtils; // 引入 Jsoup 工具类
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_config")
public class BlogConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configValue;

    private String configName;

    private Integer isPublic;
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    /** PG 没有 ON UPDATE CURRENT_TIMESTAMP，由 MetaObjectHandler 自动回填 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // 在设置 configValue 时，自动进行 HTML 清洗
    // 注意:  Lombok 的 @Setter 会覆盖这个方法，所以需要自己手动调用 Jsoup
    // 或者使用 Lombok 的 @Setter(onMethod_ = {@Autowired}) 并且在 service 层去处理，
    // 但这里为了简单，直接在 Entity 层做演示，更推荐 Service 层处理。
    // 若要 Lombok @Data 生效，请移除下面的 setConfigValue 方法，并在 Service 层做清洗。
    // 这里保留为演示，实际开发中更推荐 Service 层统一处理。

    // public void setConfigValue(String configValue) {
    //     this.configValue = JsoupUtils.cleanHtml(configValue);
    // }
}