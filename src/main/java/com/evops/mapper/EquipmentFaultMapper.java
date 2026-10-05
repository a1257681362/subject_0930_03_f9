package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.entity.EquipmentFault;
import org.apache.ibatis.annotations.Select;

public interface EquipmentFaultMapper extends BaseMapper<EquipmentFault> {

    /** 故障单号取号（H2 序列），全库唯一 */
    @Select("SELECT NEXT VALUE FOR seq_equipment_fault_no")
    Long nextFaultSeq();
}
