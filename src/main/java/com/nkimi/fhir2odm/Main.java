package com.nkimi.fhir2odm;

import ca.uhn.fhir.context.FhirContext;
import java.io.FileOutputStream;
import java.io.IOException;
import nu.xom.Document;
import nu.xom.Serializer;
import org.json.JSONException;

/**
 *
 * @author Admin
 */
public class Main {
    public static void main(String[] args) throws IOException, JSONException {
        //Testing the Converter

        //Filepath of the target file
        String filePATH = "C:\\\\Users\\\\Admin\\\\Documents\\\\questionnaire-generic.json";
        //Create HAPI FHIR context
        FhirContext ctx = FhirContext.forR4();
        //Create new instance: 1 Constructor available (FHIR Questionnaire,FHIR context)
        FHIR2ODM Converter = new FHIR2ODM(FHIR2ODM.loadQuestionnaireFromJSON(filePATH, ctx),ctx);
        //Converting the Questionaire to XML Document
        Document ODMFile = Converter.Convert();
        //Writing the Document to a file
        FileOutputStream fileOutputStream = new FileOutputStream ("C:\\\\Users\\\\Admin\\\\Documents\\\\converted.xml");
        Serializer serializer = new Serializer(fileOutputStream , "UTF-8");
        serializer.setIndent(4);
        serializer.write(ODMFile);
        serializer.flush();
        //System.out.println(ODMFile.toXML());
        
    }
}
