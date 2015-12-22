package services.rest;

import fimsExceptions.FimsRuntimeException;
import fimsExceptions.UnauthorizedRequestException;
import digester.Attribute;
import digester.Field;
import digester.Mapping;
import digester.Validation;
import org.apache.commons.digester3.Digester;
import org.json.simple.JSONObject;
import run.ConfigurationFileFetcher;
import run.Process;
import run.ProcessController;
import utils.SettingsManager;
import utils.DashboardGenerator;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * Biocode-FIMS utility services
 */
@Path("utils/")
public class Utils {
    @Context
    static ServletContext context;
    @Context
    static HttpServletResponse response;
    @Context
    static HttpServletRequest request;

    /**
     * Refresh the configuration File cache
     *
     * @return
     */
    @GET
    @Path("/refreshCache/{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryJson(
            @QueryParam("projectId") Integer projectId) {

        new ConfigurationFileFetcher(projectId, uploadPath(), false).getOutputFile();

        return Response.ok("").build();

    }


    /**
     * Get real path of the uploads folder from context.
     * Needs context to have been injected before.
     *
     * @return Real path of the uploads folder with ending slash.
     */
    static String uploadPath() {
        return context.getRealPath("tripleOutput") + File.separator;
    }

    /**
     * Retrieve a user's expeditions in a given project from Bcid. This uses an access token to access the
     * Bcid service.
     *
     * @param projectId
     *
     * @return
     */
    @GET
    @Path("/expeditionCodes/{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public void getExpeditionCodes(@PathParam("projectId") Integer projectId)
                               throws IOException, ServletException {

        RequestDispatcher dispatcher = request.getRequestDispatcher("/id/expeditionService/list/" + projectId);
        dispatcher.forward(request, response);
        return;
    }

    /**
     * Retrieve a user's graphs in a given project from Bcid. This uses an access token to access the
     * Bcid service.
     *
     * @param projectId
     *
     * @return
     */
    @GET
    @Path("/graphs/{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public void getGraphs(@PathParam("projectId") Integer projectId)
                      throws IOException, ServletException {

        RequestDispatcher dispatcher = request.getRequestDispatcher("/id/projectService/graphs/" + projectId);
        dispatcher.forward(request, response);
        return;
    }

    /**
     * Check whether or not an expedition code is valid by calling the BCID ExpeditionService/validateExpedition
     * Service
     * Should return update, insert, or error
     *
     * @param projectId
     * @param expeditionCode
     *
     * @return
     */
    @GET
    @Path("/validateExpedition/{projectId}/{expeditionCode}")
    @Produces(MediaType.APPLICATION_JSON)
    public void validateExpedition(@PathParam("projectId") Integer projectId,
                                   @PathParam("expeditionCode") String expeditionCode)
        throws IOException, ServletException {

        RequestDispatcher dispatcher = request.getRequestDispatcher("/id/expeditionService/list/" +
                projectId + "/" + expeditionCode);
        dispatcher.forward(request, response);
        return;
    }



    /**
     * Retrieve a user's expeditions in a given project from Bcid. This uses an access token to access the
     * Bcid service.
     *
     * @param projectId
     *
     * @return
     */
    @GET
    @Path("/getListFields/{list_name}/")
    @Produces(MediaType.TEXT_HTML)
    public Response getListFields(@QueryParam("projectId") Integer projectId,
                                  @PathParam("list_name") String list_name,
                                  @QueryParam("column_name") String column_name) {

        File configFile = new ConfigurationFileFetcher(projectId, uploadPath(), true).getOutputFile();

        // Create a process object
        Process p = new Process(
                projectId,
                uploadPath(),
                configFile
        );

        Validation validation = new Validation();
        p.addValidationRules(new Digester(), validation);
        digester.List results = (digester.List) validation.findList(list_name);
        // NO results mean no list has been defined!
        if (results == null) {
            return Response.ok("No list has been defined for \"" + column_name + "\" but there is a rule saying it exists.  " +
                    "Please talk to your FIMS data manager to fix this").build();
        }
        Iterator it = results.getFields().iterator();
        StringBuilder sb = new StringBuilder();

        if (column_name != null && !column_name.trim().equals("")) {
            try {
                sb.append("<b>Acceptable values for " + URLDecoder.decode(column_name, "utf-8") + "</b><br>\n");
            } catch (UnsupportedEncodingException e) {
                throw new FimsRuntimeException(500, e);
            }
        } else {
            sb.append("<b>Acceptable values for " + list_name + "</b><br>\n");
        }

        // Get field values
        while (it.hasNext()) {
            Field f = (Field)it.next();
            sb.append("<li>" + f.getValue() + "</li>\n");
        }

        return Response.ok(sb.toString()).build();
    }

    @GET
    @Path("/isNMNHProject/{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isNMNHProject(@PathParam("projectId") Integer projectId) {
        ProcessController processController = new ProcessController(projectId, null);
        Process p = new Process(
                null,
                uploadPath(),
                processController);

        return Response.ok("{\"isNMNHProject\": \"" + p.isNMNHProject() + "\"}").build();
    }

    @GET
    @Path("/listProjects")
    @Produces(MediaType.APPLICATION_JSON)
    public void listProjects()
        throws IOException, ServletException {

        RequestDispatcher dispatcher = request.getRequestDispatcher("/id/projectService/list/");
        dispatcher.forward(request, response);
        return;
    }

    @GET
    @Path("/getNAAN")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNAAN() {
        SettingsManager sm = SettingsManager.getInstance();
        sm.loadProperties();
        String naan = sm.retrieveValue("naan");

        return Response.ok("{\"naan\": \"" + naan + "\"}").build();
    }

    @GET
    @Path("/getDatasetDashboard")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatasetDashboard(@QueryParam("isNMNH") @DefaultValue("false") Boolean isNMNH) {
        HttpSession session = request.getSession();
        String username = (String) session.getAttribute("user");
        String dashboard;

        if (username == null) {
            throw new UnauthorizedRequestException("You must be logged in to view your dashboard.");
        }

        DashboardGenerator dashboardGenerator = new DashboardGenerator(username);
        if (isNMNH) {
            dashboard = dashboardGenerator.getNMNHDashboard();
        } else {
            dashboard = dashboardGenerator.getDashboard();
        }

        return Response.ok("{\"dashboard\": \"" + dashboard + "\"}").build();
    }

    @GET
    @Path("/getLatLongColumns/{projectId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLatLongColumns(@PathParam("projectId") int projectId) {
        String decimalLatDefinedBy = "http://rs.tdwg.org/dwc/terms/decimalLatitude";
        String decimalLongDefinedBy = "http://rs.tdwg.org/dwc/terms/decimalLongitude";
        JSONObject response = new JSONObject();

        try {
            ProcessController pc = new ProcessController(projectId, null);
            Process p = new Process(null, uploadPath(), pc);

            Mapping mapping = p.getMapping();
            String defaultSheet = mapping.getDefaultSheetName();
            ArrayList<Attribute> attributeList = mapping.getAllAttributes(defaultSheet);

            response.put("data_sheet", defaultSheet);

            for (Attribute attribute : attributeList) {
                // when we find the column corresponding to the definedBy for lat and long, add them to the response
                if (attribute.getDefined_by().equalsIgnoreCase(decimalLatDefinedBy)) {
                    response.put("lat_column", attribute.getColumn());
                } else if (attribute.getDefined_by().equalsIgnoreCase(decimalLongDefinedBy)) {
                    response.put("long_column", attribute.getColumn());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new FimsRuntimeException(500, e);
        }
        return Response.ok(response.toJSONString()).build();
    }

    @GET
    @Path("/getMapboxToken")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMapboxToken() {
        SettingsManager sm = SettingsManager.getInstance();
        sm.loadProperties();
        String token = sm.retrieveValue("mapboxAccessToken");

        return Response.ok("{\"accessToken\": \"" + token + "\"}").build();
    }
}
