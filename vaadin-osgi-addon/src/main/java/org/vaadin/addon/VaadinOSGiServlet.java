package org.vaadin.addon;

import com.vaadin.server.*;
import com.vaadin.shared.JsonConstants;
import com.vaadin.util.CurrentInstance;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Vaadin Ltd / mjhosio on 19/11/15.
 */
public class VaadinOSGiServlet extends VaadinServlet{


	// **** THIS IS THE OSGI SPECIFIC PART, THE REST IS JUST COPY-PASTE FROM VAADIN SERVLET

	private URL findResourceURL(String filename, ServletContext sc) throws MalformedURLException{
		URL resourceUrl = sc.getResource(filename);
		if (resourceUrl == null) {
			// try if requested file is found from classloader

			// strip leading "/" otherwise stream from JAR wont work
			if (filename.startsWith("/")) {
				filename = filename.substring(1);
			}

			resourceUrl = getService().getClassLoader().getResource(filename);
		}
		if(resourceUrl==null){
			// if we want to serve the stuff from a static folder, we can do that here

		}
		return resourceUrl;
	}

	// **** END OF OSGI PART ***

	protected boolean isStaticResourceRequest(HttpServletRequest request) {
		return request.getRequestURI().startsWith(
				request.getContextPath() + "/VAADIN/");
	}

	@Override
	protected void service(HttpServletRequest request,
						   HttpServletResponse response) throws ServletException, IOException {
		// Handle context root request without trailing slash, see #9921
		if (handleContextRootWithoutSlash(request, response)) {
			return;
		}
		CurrentInstance.clearAll();

		VaadinServletRequest vaadinRequest = createVaadinRequest(request);
		VaadinServletResponse vaadinResponse = createVaadinResponse(response);
		if (!ensureCookiesEnabled(vaadinRequest, vaadinResponse)) {
			return;
		}

		if (isStaticResourceRequest(vaadinRequest)) {

			// Define current servlet and service, but no request and response
			getService().setCurrentInstances(null, null);
			try {
				serveStaticResources(vaadinRequest, vaadinResponse);
				return;
			} finally {
				CurrentInstance.clearAll();
			}
		}
		try {
			getService().handleRequest(vaadinRequest, vaadinResponse);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
	}

	private VaadinServletResponse createVaadinResponse(
			HttpServletResponse response) {
		return new VaadinServletResponse(response, getService());
	}

	/**
	 * Create a Vaadin request for a http servlet request. This method can be
	 * overridden if the Vaadin request should have special properties.
	 *
	 * @param request
	 *            the original http servlet request
	 * @return a Vaadin request for the original request
	 */
	protected VaadinServletRequest createVaadinRequest(
			HttpServletRequest request) {
		return new VaadinServletRequest(request, getService());
	}

	/**
	 * Check if this is a request for a static resource and, if it is, serve the
	 * resource to the client.
	 *
	 * @param request
	 * @param response
	 * @return true if a file was served and the request has been handled, false
	 *         otherwise.
	 * @throws IOException
	 * @throws ServletException
	 */
	private boolean serveStaticResources(HttpServletRequest request,
										 HttpServletResponse response) throws IOException, ServletException {

		String pathInfo = request.getPathInfo();
		// path info seems to be null - why is that, this breaks osgi stuff?
//		if (pathInfo == null) {
//			return false;
//		}

		String decodedRequestURI = URLDecoder.decode(request.getRequestURI(),
				"UTF-8");
		if ((request.getContextPath() != null)
				&& (decodedRequestURI.startsWith("/VAADIN/"))) {
			serveStaticResourcesInVAADIN(decodedRequestURI, request, response);
			return true;
		}

		String decodedContextPath = URLDecoder.decode(request.getContextPath(),
				"UTF-8");
		if (decodedRequestURI.startsWith(decodedContextPath + "/VAADIN/")) {
			serveStaticResourcesInVAADIN(
					decodedRequestURI.substring(decodedContextPath.length()),
					request, response);
			return true;
		}

		return false;
	}

	/**
	 * Serve resources from VAADIN directory.
	 *
	 * @param filename
	 *            The filename to serve. Should always start with /VAADIN/.
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws ServletException
	 */
	private void serveStaticResourcesInVAADIN(String filename,
											  HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		final ServletContext sc = getServletContext();
		URL resourceUrl = findResourceURL(filename, sc);

		if (resourceUrl == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		// security check: do not permit navigation out of the VAADIN
		// directory
		if (!isAllowedVAADINResourceUrl(request, resourceUrl)) {
			getLogger()
					.log(Level.INFO,
							"Requested resource [{0}] not accessible in the VAADIN directory or access to it is forbidden.",
							filename);
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String cacheControl = "public, max-age=0, must-revalidate";
		int resourceCacheTime = getCacheTime(filename);
		if (resourceCacheTime > 0) {
			cacheControl = "max-age=" + String.valueOf(resourceCacheTime);
		}
		response.setHeader("Cache-Control", cacheControl);
		response.setDateHeader("Expires", System.currentTimeMillis()
				+ (resourceCacheTime * 1000));

		// Find the modification timestamp
		long lastModifiedTime = 0;
		URLConnection connection = null;
		try {
			connection = resourceUrl.openConnection();
			lastModifiedTime = connection.getLastModified();
			// Remove milliseconds to avoid comparison problems (milliseconds
			// are not returned by the browser in the "If-Modified-Since"
			// header).
			lastModifiedTime = lastModifiedTime - lastModifiedTime % 1000;
			response.setDateHeader("Last-Modified", lastModifiedTime);

			if (browserHasNewestVersion(request, lastModifiedTime)) {
				response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
				return;
			}
		} catch (Exception e) {
			// Failed to find out last modified timestamp. Continue without it.
			getLogger()
					.log(Level.FINEST,
							"Failed to find out last modified timestamp. Continuing without it.",
							e);
		} finally {
			try {
				// Explicitly close the input stream to prevent it
				// from remaining hanging
				// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4257700
				InputStream is = connection.getInputStream();
				if (is != null) {
					is.close();
				}
			} catch (FileNotFoundException e) {
				// Not logging when the file does not exist.
			} catch (IOException e) {
				getLogger().log(Level.INFO,
						"Error closing URLConnection input stream", e);
			}
		}

		// Set type mime type if we can determine it based on the filename
		final String mimetype = sc.getMimeType(filename);
		if (mimetype != null) {
			response.setContentType(mimetype);
		}

		writeStaticResourceResponse(request, response, resourceUrl);
	}

	/**
	 * Check that cookie support is enabled in the browser. Only checks UIDL
	 * requests.
	 *
	 * @param request
	 *            The request from the browser
	 * @param response
	 *            The response to which an error can be written
	 * @return false if cookies are disabled, true otherwise
	 * @throws IOException
	 */
	private boolean ensureCookiesEnabled(VaadinServletRequest request,
										 VaadinServletResponse response) throws IOException {
		if (ServletPortletHelper.isUIDLRequest(request)) {
			// In all other but the first UIDL request a cookie should be
			// returned by the browser.
			// This can be removed if cookieless mode (#3228) is supported
			if (request.getRequestedSessionId() == null) {
				// User has cookies disabled
				SystemMessages systemMessages = getService().getSystemMessages(
						ServletPortletHelper.findLocale(null, null, request),
						request);
				getService().writeStringResponse(
						response,
						JsonConstants.JSON_CONTENT_TYPE,
						VaadinService.createCriticalNotificationJSON(
								systemMessages.getCookiesDisabledCaption(),
								systemMessages.getCookiesDisabledMessage(),
								null, systemMessages.getCookiesDisabledURL()));
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the browser has an up to date cached version of requested
	 * resource. Currently the check is performed using the "If-Modified-Since"
	 * header. Could be expanded if needed.
	 *
	 * @param request
	 *            The HttpServletRequest from the browser.
	 * @param resourceLastModifiedTimestamp
	 *            The timestamp when the resource was last modified. 0 if the
	 *            last modification time is unknown.
	 * @return true if the If-Modified-Since header tells the cached version in
	 *         the browser is up to date, false otherwise
	 */
	private boolean browserHasNewestVersion(HttpServletRequest request,
											long resourceLastModifiedTimestamp) {
		if (resourceLastModifiedTimestamp < 1) {
			// We do not know when it was modified so the browser cannot have an
			// up-to-date version
			return false;
		}
        /*
         * The browser can request the resource conditionally using an
         * If-Modified-Since header. Check this against the last modification
         * time.
         */
		try {
			// If-Modified-Since represents the timestamp of the version cached
			// in the browser
			long headerIfModifiedSince = request
					.getDateHeader("If-Modified-Since");

			if (headerIfModifiedSince >= resourceLastModifiedTimestamp) {
				// Browser has this an up-to-date version of the resource
				return true;
			}
		} catch (Exception e) {
			// Failed to parse header. Fail silently - the browser does not have
			// an up-to-date version in its cache.
		}
		return false;
	}

	private static final Logger getLogger() {
		return Logger.getLogger(VaadinOSGiServlet.class.getName());
	}
}
