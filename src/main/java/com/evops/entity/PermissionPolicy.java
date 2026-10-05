package com.evops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限策略（单行 id=1）：文物修复相关操作的收紧开关。
 *
 * <p>{@code repairPhotoUploadOpen=false} 表示博物馆文物修复权限被收紧，
 * 影像上传立即停止推进；已确认的影像版本、物理文件与台账事实不受影响、全部保留。</p>
 */
@Data
@TableName("t_permission_policy")
public class PermissionPolicy {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 影像上传是否开放：true 开放 / false 已收紧 */
    private Boolean repairPhotoUploadOpen;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
