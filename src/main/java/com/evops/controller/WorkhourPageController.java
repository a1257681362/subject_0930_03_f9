package com.evops.controller;

import com.evops.service.WorkhourService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 修复工时与设备占用排程页面（museum-9）。
 * 与 CSV 导出、后台 JSON 共用 {@link WorkhourService#schedule} 同一查询口径：
 * 修复对象置为无效（工单作废 / 文物撤销）后三处一致剔除。
 */
@Controller
public class WorkhourPageController {

    private final WorkhourService workhourService;

    public WorkhourPageController(WorkhourService workhourService) {
        this.workhourService = workhourService;
    }

    @GetMapping("/workhour-schedule")
    public String schedulePage(@RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @RequestParam(required = false) Long equipmentId,
                               @RequestParam(required = false) Long projectId,
                               Model model) {
        model.addAttribute("rows", workhourService.schedule(
                WorkhourController.parseDateTime(from), WorkhourController.parseDateTime(to),
                equipmentId, projectId));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "workhour-schedule";
    }
}
