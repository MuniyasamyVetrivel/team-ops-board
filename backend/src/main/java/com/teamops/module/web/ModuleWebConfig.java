package com.teamops.module.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.teamops.module.service.ModuleAccessService;

/** Puts {@link ModuleAccessInterceptor} in front of every department tool endpoint ({@code /api/modules/**}). */
@Configuration(proxyBeanMethods = false)
public class ModuleWebConfig implements WebMvcConfigurer {

	private final ObjectProvider<ModuleAccessService> moduleAccess;

	public ModuleWebConfig(ObjectProvider<ModuleAccessService> moduleAccess) {
		this.moduleAccess = moduleAccess;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new ModuleAccessInterceptor(moduleAccess)).addPathPatterns("/api/modules/**");
	}

}
