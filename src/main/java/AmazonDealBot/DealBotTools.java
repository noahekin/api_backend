package AmazonDealBot;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

//alle einzelnen Hilfsmethoden hier ablegen
public class DealBotTools {
	
	/**
     * Methode zum Erstellen von Amazon Affiliate Links.
     * @param asin       die ASIN des gewünschten Links.
     */
	public static String generateAmazonAffiliateLink(String asin) {
	    final String PARTNER_ID = "deallabs07c-21";
	    return "https://www.amazon.de/dp/" + asin + "/?tag=" + PARTNER_ID;
	}
	
	/**
     * Methode zum Schreiben von Daten in Google Sheets.
     *
     * @param spreadsheetId        Die Google Sheets ID des Ziel-Dokuments.
     * @param serviceAccountKeyPath Der Pfad zur JSON-Schlüsseldatei für das Service-Konto.
     * @param sheetName            Name des Sheets, in das geschrieben werden soll.
     * @param data                 Die Liste der Zeilen, die eingefügt werden sollen.
     * JSON Key muss neu abgelegt werden, 
     * Spreadsheet id:1vutJJFAOzz4fIaOaE0CBAwecMYLKhA6gj9qT3erUCNY
     * Application name: Keepa Deals to Google Sheets
     */
    public static void writeToGoogleSheets(String spreadsheetId, String serviceAccountKeyPath, 
                                           String sheetName, List<List<Object>> data) {
        try {
            // JSON Factory direkt in der Methode
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = GoogleCredential.fromStream(new FileInputStream(serviceAccountKeyPath))
                    .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));

            Sheets sheetsService = new Sheets.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("Keepa Deals to Google Sheets")
                    .build();

            String range = sheetName + "!A1"; // Beginn des Schreibens ab der ersten Zeile
            ValueRange body = new ValueRange().setValues(data);

            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute();

            System.out.println("✅ Daten erfolgreich in " + sheetName + " geschrieben!");
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("❌ Fehler beim Schreiben in Google Sheets: " + e.getMessage());
        }
    }
}
