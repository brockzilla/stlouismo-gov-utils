import java.util.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;

/**
 * Look up the designated public schools serving a given address in the city of St. Louis.
 *
 * Since the city doesn't provide a proper API, we're force to issue requests to their website
 * and piece together the results.
 */
public class StLouisPublicSchoolLookup {

    private static final String ADDRESS_LOOKUP_URL = "https://www.stlouis-mo.gov/data/address-search/index.cfm";

    public StLouisPublicSchoolLookup() {
    }

    public static void main(String[] args) {

        String address = String.join(" ", args);
        if (address == null || address.trim().indexOf(" ") < 0) {
            System.out.println("Please pass in a valid street address, ie. 2919 Russell Blvd");
        } else {
            Collection<String> schoolNames = getPublicSchoolsForAddress(address);
            for (String schoolName : schoolNames) {
                System.out.println(" - " + schoolName);
            }
        }
    }

    private static Collection<String> getPublicSchoolsForAddress(String pAddress) {
        Collection<String> schoolNames = new ArrayList<String>();

        String parcelId = lookupParcelId(pAddress);

        if (parcelId == null) {
            System.out.println("Failed to look up school info for address: " + pAddress + ", no parcelId");
        } else {

            HttpURLConnection connection = null;

            try {
                String url = ADDRESS_LOOKUP_URL +
                    "?parcelId=" + parcelId +
                    "&CategoryBy=form.start,form.Schools" +
                    "&firstview=true";

                System.out.println("Issuing request to: " + url);

                connection = (HttpURLConnection)new URL(url).openConnection();
                String body = readString(connection.getInputStream());

                schoolNames.addAll(getSchoolNames(body, "<h4>Preschool</h4>", "<h4>"));
                schoolNames.addAll(getSchoolNames(body, "<h4>Elementary school</h4>", "<h4>"));
                schoolNames.addAll(getSchoolNames(body, "<h4>Middle school</h4>", "<h4>"));
                schoolNames.addAll(getSchoolNames(body, "<h4>High school</h4>", "</div>"));

            } catch (Exception e) {
                e.printStackTrace();

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            System.out.println("Found " + schoolNames.size() + " public schools serving address: " + pAddress);
        }

        return schoolNames;
    }


    /** Do the nasty URL parsing */
    private static Collection<String> getSchoolNames(String pRawHTML, String pStartMarker, String pEndMarker) {
        Collection<String> schoolNames = new ArrayList<String>();

        //System.out.println("Parsing school names for startMarker: [" + pStartMarker + "]");

        if (pRawHTML.indexOf(pStartMarker) > 0) {
            pRawHTML = pRawHTML.substring(pRawHTML.indexOf(pStartMarker)+pStartMarker.length(), pRawHTML.length());
            pRawHTML = pRawHTML.substring(0, pRawHTML.indexOf(pEndMarker));

            //System.out.println("Got Raw HTML: [" + pRawHTML + "]");

            // first, we check for multiple school records
            Collection<String> records = new ArrayList<String>();
            while (pRawHTML.indexOf("<p>") >= 0) {
                String record = pRawHTML.substring(pRawHTML.indexOf("<p>")+3, pRawHTML.length());
                record = record.substring(0, record.indexOf("</p>"));
                records.add(record);
                pRawHTML = pRawHTML.substring(pRawHTML.indexOf("</p>")+4, pRawHTML.length());
            }

            for (String record : records) {
                String[] lines = record.split("<br>");
                if (lines[0].indexOf("Age") >= 0 || lines[0].indexOf("Grade") >= 0) {
                    schoolNames.add(lines[1].trim());
                } else {
                    schoolNames.add(lines[0].trim());
                }
            }
        }

        return schoolNames;
    }

    /**
      * Given an address snippet, use the city's website to lookup a parcel id
      * Terribly ugly -- we have to catch an error and gleen the parcelId from a redirect
      *
      * http://www.stlouis-mo.gov/data/address-search/index.cfm?Schools=Schools&everyWhere=everyWhere&streetAddress=2919%20russell%20blvd&findByAddress=Search&ResidentialServices=true
      */
    private static String lookupParcelId(String pAddress) {
        String parcelId = "";

        HttpsURLConnection connection = null;

        // We need only the street address
        String streetAddress = pAddress;
        if (streetAddress.indexOf(",") > 0) {
            streetAddress = streetAddress.substring(0, streetAddress.indexOf(","));
        }

        try {
            String url = ADDRESS_LOOKUP_URL;
            String urlParameters =
                 "Schools=Schools" +
                 "&everyWhere=everyWhere" +
                 "&streetAddress=" + pAddress +
                 "&findByAddress=Search";

            System.out.println("INFO: Issuing request to: " + url + "?" + urlParameters);

            connection = (HttpsURLConnection)new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes("UTF-8").length));
            connection.setRequestProperty("Content-Language", "en-US");
            connection.setUseCaches (false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setFollowRedirects(false);
            connection.setInstanceFollowRedirects(false);

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(urlParameters);
            writer.flush();
            writer.close();

            // the above connection will redirect to a url that looks like this:
            // /data/address-search/index.cfm?addr=2919 RUSSELL BLVD&stname=RUSSELL&stnum=2919&parcelId=13100001100&CategoryBy=form.start,form.Schools&firstview=true
            String redirectURL = connection.getHeaderField("Location");
            if (redirectURL != null && redirectURL.indexOf("parcelId") > 0) {
                redirectURL = redirectURL.substring(redirectURL.indexOf("parcelId")+9, redirectURL.length());
                parcelId = redirectURL.substring(0, redirectURL.indexOf("&"));
            }

            // the redirect will break it here, so now we move into the catch block...

        } catch (Exception e) {
            System.out.println("Trouble looking up parcelId for address: " + pAddress);
            e.printStackTrace();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return parcelId;
    }

    private static String readString(InputStream pInput) {
        try {
            BufferedInputStream in = new BufferedInputStream(pInput);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int result = in.read();
            while (result != -1) {
                out.write((byte)result);
                result = in.read();
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            System.out.println("Trouble reading in html");
            e.printStackTrace();
            return null;
        }
    }
}



