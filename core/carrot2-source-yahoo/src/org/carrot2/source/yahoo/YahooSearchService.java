package org.carrot2.source.yahoo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.carrot2.core.attribute.Init;
import org.carrot2.core.attribute.Processing;
import org.carrot2.source.MultipageSearchEngineMetadata;
import org.carrot2.source.SearchEngineResponse;
import org.carrot2.util.CloseableUtils;
import org.carrot2.util.StreamUtils;
import org.carrot2.util.attribute.*;
import org.carrot2.util.httpclient.HttpClientFactory;
import org.carrot2.util.httpclient.HttpHeaders;
import org.xml.sax.*;

/**
 * A superclass shared between Web and News searching services.
 */
@Bindable(prefix = "YahooSearchService")
public abstract class YahooSearchService
{
    /** Logger for this object. */
    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Query types.
     */
    public enum QueryType
    {
        /**
         * Returns results with all query terms.
         */
        ALL,

        /**
         * Returns results with one or more of the query terms.
         */
        ANY,

        /**
         * Returns results containing the query terms as a phrase.
         */
        PHRASE;

        @Override
        public String toString()
        {
            return this.name().toLowerCase();
        }
    }

    /**
     * Metadata key for the first result's index.
     * 
     * @see SearchEngineResponse#metadata
     */
    public static final String FIRST_INDEX_KEY = "firstIndex";

    /**
     * Metadata key for the number of results actually returned.
     * 
     * @see SearchEngineResponse#metadata
     */
    public static final String RESULTS_RETURNED_KEY = "resultsReturned";

    /**
     * Application ID required for Yahoo! services. Please obtain your own appid for
     * production deployments.
     * 
     * @label Application ID
     * @level Advanced
     * @group Service
     */
    @Init
    @Input
    @Attribute
    public String appid = "carrotsearch";

    /**
     * Query words interpretation.
     * 
     * @group Search query
     * @level Medium
     */
    @Processing
    @Input
    @Attribute
    public QueryType type = QueryType.ALL;

    /*
     * TODO: Yahoo API has a broken link to language codes. The format of these
     * language codes is also undetermined -- the official search page allows you to pass
     * more than one language, is it possible via the API as well?
     */
    /**
     * The language the results are written in. Value must be one of the 
     * supported language codes. Omitting language returns results in any language.
     * 
     * @group Results filtering
     * @label Language
     * @level Medium
     */
    @Processing
    @Input
    @Attribute
    public String language;

    /**
     * Yahoo! engine current metadata.
     */
    protected MultipageSearchEngineMetadata metadata = DEFAULT_METADATA;

    /**
     * Yahoo! engine default metadata.
     */
    final static MultipageSearchEngineMetadata DEFAULT_METADATA = new MultipageSearchEngineMetadata(
        50, 1000);

    /**
     * Keeps subclasses to this package.
     */
    YahooSearchService()
    {
    }

    /**
     * Prepare an array of {@link NameValuePair} (parameters for the request).
     */
    protected abstract ArrayList<NameValuePair> createRequestParams(String query,
        int start, int results);

    /**
     * @return Return service URI for this service.
     */
    protected abstract String getServiceURI();

    /**
     * Sends a search query to Yahoo! and parses the result.
     */
    protected final SearchEngineResponse query(String query, int start, int results)
        throws IOException
    {
        // Yahoo's results start from 1.
        start++;
        results = Math.min(results, metadata.resultsPerPage);

        final HttpClient client = HttpClientFactory.getTimeoutingClient();
        client.getParams().setVersion(HttpVersion.HTTP_1_1);

        InputStream is = null;
        final GetMethod request = new GetMethod();
        try
        {
            request.setURI(new URI(getServiceURI(), false));
            request.setRequestHeader(HttpHeaders.URL_ENCODED);
            request.setRequestHeader(HttpHeaders.GZIP_ENCODING);
            request.setRequestHeader(HttpHeaders.USER_AGENT_HEADER_MOZILLA);

            final ArrayList<NameValuePair> params = createRequestParams(query, start,
                results);
            params.add(new NameValuePair("output", "xml"));
            request.setQueryString(params.toArray(new NameValuePair [params.size()]));

            if (logger.isDebugEnabled())
            {
                logger.debug("Request params: " + request.getQueryString());
            }
            final int statusCode = client.executeMethod(request);

            // Unwrap compressed streams.
            is = request.getResponseBodyAsStream();
            final Header encoded = request.getResponseHeader("Content-Encoding");
            final String compressionUsed;
            if (encoded != null && "gzip".equalsIgnoreCase(encoded.getValue()))
            {
                logger.debug("Unwrapping GZIP compressed stream.");
                compressionUsed = "gzip";
                is = new GZIPInputStream(is);
            }
            else
            {
                compressionUsed = "(uncompressed)";
            }

            if (statusCode == HttpStatus.SC_OK
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE
                || statusCode == HttpStatus.SC_BAD_REQUEST)
            {
                // Parse the data stream.
                final SearchEngineResponse response = parseResponseXML(is);
                response.metadata.put(SearchEngineResponse.COMPRESSION_KEY,
                    compressionUsed);

                if (logger.isDebugEnabled())
                {
                    logger.debug("Received, results: " + response.results.size()
                        + ", total: " + response.getResultsTotal() + ", first: "
                        + response.metadata.get(FIRST_INDEX_KEY));
                }

                return response;
            }
            else
            {
                // Read the output and throw an exception.
                final String m = "Yahoo returned HTTP Error: " + statusCode
                    + ", HTTP payload: "
                    + new String(StreamUtils.readFully(is), "iso8859-1");
                logger.warn(m);
                throw new IOException(m);
            }
        }
        finally
        {
            if (is != null)
            {
                CloseableUtils.close(is);
            }
            request.releaseConnection();
        }
    }

    /**
     * Parse the response stream, assuming it is XML.
     */
    private static SearchEngineResponse parseResponseXML(final InputStream is)
        throws IOException
    {
        try
        {
            final XMLResponseParser parser = new XMLResponseParser();
            final XMLReader reader = SAXParserFactory.newInstance().newSAXParser()
                .getXMLReader();

            reader.setFeature("http://xml.org/sax/features/validation", false);
            reader.setFeature("http://xml.org/sax/features/namespaces", true);
            reader.setContentHandler(parser);

            reader.parse(new InputSource(is));

            return parser.response;
        }
        catch (final SAXException e)
        {
            final Throwable cause = e.getException();
            if (cause != null && cause instanceof IOException)
            {
                throw (IOException) cause;
            }
            throw new IOException("XML parsing exception: " + e.getMessage());
        }
        catch (final ParserConfigurationException e)
        {
            throw new IOException("Could not acquire XML parser.");
        }
    }

}