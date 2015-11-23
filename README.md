This project is a simple proof of concept how to use Vaadin in OSGi environment using the WhiteBoard pattern.
The project consists of two modules:

The vaadin-osgi-addon module provides a modified version of the VaadinServlet. The servlet is modified in a way that
enables it to handle static resources in osgi environment.

The vaadin-osgi-addon-demo module provides the actual poc where we deploy vaadin as OSGi DS. The POC has been tested using
Karaf 4.0.3.

The ultimate goal is to come up with an add-on that provides support for injecting OSGi DS services to UI:s created by the servlet.

