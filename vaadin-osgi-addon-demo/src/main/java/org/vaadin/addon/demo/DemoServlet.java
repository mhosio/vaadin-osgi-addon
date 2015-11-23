package org.vaadin.addon.demo;

import com.vaadin.annotations.VaadinServletConfiguration;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.vaadin.addon.VaadinOSGiServlet;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by Vaadin Ltd / mjhosio on 19/11/15.
 */
@Component(name = "foo", immediate = true, property = {
		"alias=/"}, service = {Servlet.class})
@VaadinServletConfiguration(productionMode = false, ui = DemoUI.class, widgetset = "org.vaadin.addon.demo.DemoWidgetSet")
public class DemoServlet extends VaadinOSGiServlet implements
		Servlet, ServiceTrackerCustomizer<ServletContext, ServiceReference<ServletContext>> {

	Logger logger= Logger.getLogger(DemoServlet.class.getName());

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		super.service(request, response);
	}

	@Override
	public ServiceReference<ServletContext> addingService(
			ServiceReference<ServletContext> serviceReference) {

		String contextPath = (String) serviceReference
				.getProperty("osgi.web.contextpath");

		return serviceReference;
	}

	@Override
	public void modifiedService(
			ServiceReference<ServletContext> serviceReference,
			ServiceReference<ServletContext> trackedServiceReference) {

		removedService(serviceReference, trackedServiceReference);

	}

	@Override
	public void removedService(ServiceReference<ServletContext> serviceReference,
							   ServiceReference<ServletContext> trackedServiceReference) {

	}

	@Activate
	@Modified
	protected void activate(ComponentContext componentContext,
							Map<String, Object> properties) throws Exception {

		if (_serviceTracker != null) {
			_serviceTracker.close();
		}

		Filter filter = FrameworkUtil
				.createFilter("(&(objectClass="
						+ ServletContext.class.getName()
						+ ")(osgi.web.contextpath=*))");

		_serviceTracker = new ServiceTracker<ServletContext, ServiceReference<ServletContext>>(
				componentContext.getBundleContext(), filter, this);

		_serviceTracker.open();
	}

	@Deactivate
	protected void deactivate() {
		_serviceTracker.close();

		_serviceTracker = null;
	}

	private ServiceTracker<ServletContext, ServiceReference<ServletContext>> _serviceTracker;

}
