package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.entity.RepairEquipment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface RepairEquipmentMapper extends BaseMapper<RepairEquipment> {

    /**
     * 设备行锁（同时段占用互斥串行化）：对设备行执行一次 UPDATE 占位，
     * H2 行级排他锁持有到当前事务提交——并发的第二个填报在此阻塞，
     * 待前者提交后再在事务内复查时段重叠。必须在事务内调用。
     */
    @Update("UPDATE t_repair_equipment SET update_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int lockRow(@Param("id") Long id);
}
