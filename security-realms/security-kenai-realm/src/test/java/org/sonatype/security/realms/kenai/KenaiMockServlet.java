package org.sonatype.security.realms.kenai;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;

public class KenaiMockServlet
    extends HttpServlet
{
    
    private int totalProjectSize = 301;
    
    public static String TOTAL_PROJECTS_KEY = "totalProjects";

    /**
     * Genearted serial uid.
     */
    private static final long serialVersionUID = -881495552351752305L;
    
    @Override
    public void init( ServletConfig config )
        throws ServletException
    {
        super.init( config );
        
        String totalProjectsParam = config.getInitParameter( TOTAL_PROJECTS_KEY );
        if( totalProjectsParam != null )
        {
            totalProjectSize = Integer.parseInt( totalProjectsParam );
        }
    }

    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp )
        throws ServletException,
            IOException
    {
        
        int pageSize = 200;
        int pageIndex = 1;
        
        String sizeParam = req.getParameter( "size" );
        if( sizeParam != null )
        {
            pageSize = Integer.parseInt( sizeParam );
        }
        
        String pageIndexParam = req.getParameter( "page" );
        if( pageIndexParam != null )
        {
            pageIndex = Integer.parseInt( pageIndexParam );
        }
        
        String reqUrl = req.getRequestURL().substring( 0, req.getRequestURL().indexOf( "/api/" ) );
        
        
        try
        {
            String output = new KenaiProjectJsonGenerator( pageSize, totalProjectSize, reqUrl ).generate( pageIndex );
            resp.getOutputStream().write( output.getBytes() );
        }
        catch ( JSONException e )
        {
            this.log( "Failed to generate JSON", e );
            resp.setStatus( 500 );
        } 
    }    

}
