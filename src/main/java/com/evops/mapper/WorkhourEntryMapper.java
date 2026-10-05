package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.entity.WorkhourEntry;
import org.apache.ibatis.annotations.Select;

public interface WorkhourEntryMapper extends BaseMapper<WorkhourEntry> {

    /** 填报链号取号（H2 序列），全库唯一 */
    @Select("SELECT NEXT VALUE FOR seq_workhour_chain_no")
    Long nextChainSeq();
}
