package com.evops.config;

import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiroConfig {
    @Bean
    public Realm localRealm() {
        SimpleAccountRealm realm = new SimpleAccountRealm();
        realm.addAccount("bootstrap", "bootstrap", "system");
        return realm;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        // 台账模块为本地登记系统，REST 接口暂不挂登录；健康检查同样匿名
        chain.addPathDefinition("/api/health", "anon");
        chain.addPathDefinition("/api/**", "anon");
        // 修复工时与设备占用排程页面（与 /api 后台、CSV 导出同口径）
        chain.addPathDefinition("/workhour-schedule", "anon");
        chain.addPathDefinition("/error", "anon");
        chain.addPathDefinition("/**", "authc");
        return chain;
    }
}
