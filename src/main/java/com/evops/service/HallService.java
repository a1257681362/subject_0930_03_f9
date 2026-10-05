package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.dto.HallCreateRequest;
import com.evops.dto.HallRelicRequest;
import com.evops.entity.Hall;
import com.evops.entity.HallRelic;
import com.evops.entity.Relic;
import com.evops.mapper.HallMapper;
import com.evops.mapper.HallRelicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.evops.constant.MuseumConstants.*;

/**
 * 展厅与陈展：展厅建档、文物上展/撤展。
 * 巡检任务按展厅生成；撤展文物不再采样，但历史读数/事件保留。
 */
@Service
public class HallService {

    private final HallMapper hallMapper;
    private final HallRelicMapper hallRelicMapper;
    private final RelicService relicService;

    public HallService(HallMapper hallMapper,
                       HallRelicMapper hallRelicMapper,
                       RelicService relicService) {
        this.hallMapper = hallMapper;
        this.hallRelicMapper = hallRelicMapper;
        this.relicService = relicService;
    }

    @Transactional
    public Hall create(HallCreateRequest req) {
        if (hallMapper.selectCount(new LambdaQueryWrapper<Hall>()
                .eq(Hall::getHallNo, req.getHallNo())) > 0) {
            throw new IllegalArgumentException("展厅编号已存在: " + req.getHallNo());
        }
        Hall hall = new Hall();
        hall.setHallNo(req.getHallNo());
        hall.setName(req.getName());
        hall.setLocation(req.getLocation());
        hall.setCustodian(req.getCustodian());
        hall.setStatus(HALL_ACTIVE);
        hallMapper.insert(hall);
        return hall;
    }

    public Hall requireHall(Long hallId) {
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) {
            throw new IllegalArgumentException("展厅不存在: id=" + hallId);
        }
        return hall;
    }

    public List<Hall> listHalls(String status) {
        return hallMapper.selectList(new LambdaQueryWrapper<Hall>()
                .eq(status != null && !status.isEmpty(), Hall::getStatus, status)
                .orderByAsc(Hall::getId));
    }

    /** 文物上展厅陈展：材质取档案快照，按点位归组采样 */
    @Transactional
    public HallRelic display(Long hallId, HallRelicRequest req) {
        Hall hall = requireHall(hallId);
        if (!HALL_ACTIVE.equals(hall.getStatus())) {
            throw new IllegalArgumentException("展厅已关闭，不能继续布展: " + hall.getHallNo());
        }
        Relic relic = relicService.requireRelic(req.getRelicId());
        if (!ACTIVE.equals(relic.getStatus())) {
            throw new IllegalArgumentException("文物已撤销，不能陈展: " + relic.getRelicNo());
        }
        HallRelic existing = hallRelicMapper.selectOne(new LambdaQueryWrapper<HallRelic>()
                .eq(HallRelic::getHallId, hallId)
                .eq(HallRelic::getRelicId, req.getRelicId()));
        if (existing != null) {
            if (DISPLAY_ON.equals(existing.getStatus())) {
                throw new IllegalArgumentException("文物已在该展厅陈展中: " + relic.getRelicNo());
            }
            existing.setPointCode(req.getPointCode());
            existing.setDisplayNo(req.getDisplayNo());
            existing.setMaterial(relic.getMaterial());
            existing.setStatus(DISPLAY_ON);
            hallRelicMapper.updateById(existing);
            return existing;
        }
        HallRelic hallRelic = new HallRelic();
        hallRelic.setHallId(hallId);
        hallRelic.setRelicId(req.getRelicId());
        hallRelic.setMaterial(relic.getMaterial());
        hallRelic.setPointCode(req.getPointCode());
        hallRelic.setDisplayNo(req.getDisplayNo());
        hallRelic.setStatus(DISPLAY_ON);
        hallRelicMapper.insert(hallRelic);
        return hallRelic;
    }

    /** 撤展：只置状态，历史读数与事件保留可查 */
    @Transactional
    public HallRelic withdraw(Long hallId, Long relicId) {
        requireHall(hallId);
        HallRelic hallRelic = hallRelicMapper.selectOne(new LambdaQueryWrapper<HallRelic>()
                .eq(HallRelic::getHallId, hallId)
                .eq(HallRelic::getRelicId, relicId));
        if (hallRelic == null) {
            throw new IllegalArgumentException("该文物未在此展厅陈展: relicId=" + relicId);
        }
        if (!DISPLAY_ON.equals(hallRelic.getStatus())) {
            throw new IllegalArgumentException("文物非展出中状态，不能撤展");
        }
        hallRelic.setStatus(DISPLAY_WITHDRAWN);
        hallRelicMapper.updateById(hallRelic);
        return hallRelic;
    }

    public List<HallRelic> listOnDisplay(Long hallId) {
        return hallRelicMapper.selectList(new LambdaQueryWrapper<HallRelic>()
                .eq(HallRelic::getHallId, hallId)
                .eq(HallRelic::getStatus, DISPLAY_ON)
                .orderByAsc(HallRelic::getPointCode)
                .orderByAsc(HallRelic::getId));
    }
}
