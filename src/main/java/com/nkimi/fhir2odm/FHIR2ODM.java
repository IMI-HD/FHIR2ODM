package com.nkimi.fhir2odm;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Serializer;
import org.hl7.fhir.r4.model.*;
import org.json.JSONException;
import org.json.JSONObject;

public class FHIR2ODM {
    
    //fields of the class: 1x FHIRQuestionnaire to convert, 1xODMFileBuilder, 1xFHIRContext
    private final Questionnaire FHIRQuestionaire;
    ODMFileBuilder ODMBuilder;
    private final FhirContext ctx;
    
    //1x Constructor
    public FHIR2ODM(Questionnaire FHIRQuestionnaire, FhirContext ctx) {
        this.FHIRQuestionaire = FHIRQuestionnaire;
        this.ctx = ctx;
    }
    

    //For the purpose of the project (JSON File support) method to load FHIRQuestionnaire from JSON File and parse it
    //Method can be called static (in case of future projects)
    public static Questionnaire loadQuestionnaireFromJSON(String filePATH, FhirContext ctx) throws JSONException, IOException{
        Questionnaire FHIRQuestionnaire;
        IParser parser = ctx.newJsonParser();
        String JSONFile = new String(Files.readAllBytes(Paths.get(filePATH)), StandardCharsets.UTF_8);
        JSONObject JSONQuestionnaire = new JSONObject(JSONFile);
        FHIRQuestionnaire = parser.parseResource(Questionnaire.class, JSONQuestionnaire.toString());
        return FHIRQuestionnaire;
    }
    
    //This function will loop through itemList with unknown number of nested Items flattening the structure
    //Parameters: List of ItemComponents and loops through all nested elements 
    //adding them to a new List of ItemComponents. Only Componments with no nested items will be added to the List
    //All other groups will be added to the description of the ItemDef
    public List <Questionnaire.QuestionnaireItemComponent> flattenItemList( List <Questionnaire.QuestionnaireItemComponent> itemList){
        List <Questionnaire.QuestionnaireItemComponent> flattenedItemList;
        flattenedItemList = new ArrayList<>();
        for(Questionnaire.QuestionnaireItemComponent itemComponent:itemList)
                {
                    if(itemComponent.hasItem())
                    {
                        flattenedItemList.addAll(flattenItemList(itemComponent.getItem()));
                    }
                    flattenedItemList.add(itemComponent);
                    
                }
        return flattenedItemList;
    }
    
    //This function does the conversion for each FHIR Questionnaire Item to the corresponding ODM one
    //It writes the Conversion to the ODMBuilder of the class
    private void ConversionLogic(Questionnaire.QuestionnaireItemComponent itemComponent){
        Integer index;      
        String Item_OID = itemComponent.getLinkId();
        String Item_Name = (itemComponent.getText()==null)?"Name not defined":itemComponent.getText().replace('"',' ');
        
        //Unsupported format
        if(itemComponent.hasAnswerValueSet()){
            index = this.ODMBuilder.addItemDef(
                    new AttributePair("OID",Item_OID),
                    new AttributePair("Name","Items with answerValueSet not supported."),
                    new AttributePair("DataType",ConvertFHIRTypeToODMType(itemComponent.getType()))
                );
            return;
        }
        
        if(itemComponent.hasAnswerOption()){
            
               index = this.ODMBuilder.addItemDef(
                    new AttributePair("OID",Item_OID),
                    new AttributePair("Name",Item_Name),
                    new AttributePair("DataType","text")
                ); 
               
               //Adding the CodeListRef to the ItemDef
               Element CodeListRef = new Element("CodeListRef");
               CodeListRef.addAttribute(new Attribute("CodeListOID","CL_"+Item_OID));
               this.ODMBuilder.addElementToItemDef(index, CodeListRef);
               
               //Adding the CodeList to the ODMFileBuilder for this ItemDef
               index = this.ODMBuilder.addCodeList(
                    new AttributePair("OID","CL_"+Item_OID),
                    new AttributePair("Name",Item_Name)
               );
               
               //Loop through AnswerOptions and add CodeListItem to CodeList for each
               List<Questionnaire.QuestionnaireItemAnswerOptionComponent> AnswerOptionList;
               Element CodeListItem,Decode,TranslatedText;
               Integer CodedValue=0;
               AnswerOptionList = itemComponent.getAnswerOption();
               for(Questionnaire.QuestionnaireItemAnswerOptionComponent AnswerOption:AnswerOptionList){
                   //Create the CodeListItem XML Element
                   //Create Decode Element with TranslatedText as child of CodedListItem
                   Decode = new Element("Decode");
                   CodeListItem = new Element("CodeListItem");
                   TranslatedText = new Element("TranslatedText");
                   
                   //Default language en
                   TranslatedText.addNamespaceDeclaration("xml", "http://www.w3.org/XML/1998/namespace");
                   TranslatedText.addAttribute(new Attribute("xml:lang","http://www.w3.org/XML/1998/namespace","en"));
                   
                   //Check if ValueCoding is available
                   if(AnswerOption.hasValueCoding()){
                       
                        CodeListItem.addAttribute(new Attribute("CodedValue",AnswerOption.getValueCoding().getCode())); 
                        TranslatedText.appendChild(AnswerOption.getValueCoding().getDisplay());
                        
                   }else{
                       
                        CodeListItem.addAttribute(new Attribute("CodedValue",CodedValue+""));
                        TranslatedText.appendChild(AnswerOption.getValue().toString());
                        
                   }
                                    
                   Decode.appendChild(TranslatedText);
                   CodeListItem.appendChild(Decode);
                   this.ODMBuilder.addElementToCodeListList(index, CodeListItem);
                   CodedValue++;
               }
        }else{
        //if no answeroptions available handle the item as generic and let the ConvertFHIRTypeToODMType decide
                index = this.ODMBuilder.addItemDef(
                    new AttributePair("OID",Item_OID),
                    new AttributePair("Name",Item_Name),
                    new AttributePair("DataType",ConvertFHIRTypeToODMType(itemComponent.getType()))
                ); 
                
        //check if there is getMaxLength
                if(itemComponent.getMaxLength()!=0) {
                this.ODMBuilder.addAttributesToItemDef(index, new AttributePair("Length",""+itemComponent.getMaxLength()));
                }

        }
    }
    
    //This function will return the ODMDataType for the particular FHIR type
    public String ConvertFHIRTypeToODMType(Questionnaire.QuestionnaireItemType FHIRType){
        String ODMDataType;
        switch(FHIRType){
            case ATTACHMENT: ODMDataType="hexBinary"; break;
            case BOOLEAN: ODMDataType="boolean"; break;
            case DATE: ODMDataType="date"; break;
            case DATETIME: ODMDataType="datetime"; break;
            case DECIMAL: ODMDataType="decimal"; break;
            case DISPLAY: ODMDataType="text"; break;
            case INTEGER: ODMDataType="integer"; break;
            case NULL: ODMDataType="text"; break;
            case STRING: ODMDataType="string"; break;
            case TEXT: ODMDataType="text"; break;
            case TIME: ODMDataType="time"; break;
            case URL: ODMDataType="URI"; break;
            default: ODMDataType="unknown"; break;
        }
        return ODMDataType;
    }
    public Document Convert(){
        //Defining the important variables for the method
        Questionnaire FHIRQuestionnaire = this.FHIRQuestionaire;
        String title = (FHIRQuestionnaire.getTitle()==null)?"Tittle not defined":FHIRQuestionnaire.getTitle();
        this.ODMBuilder = new ODMFileBuilder(title);
        List <Questionnaire.QuestionnaireItemComponent> itemList = FHIRQuestionnaire.getItem();
        List <Questionnaire.QuestionnaireItemComponent> subItemList;
        List <Questionnaire.QuestionnaireItemComponent> subSubItemList;
        Integer ODMGroupsCount=1;
        Integer ODMItemCount=0;
        
        
        //Creating a group for all elements who belong to the questionnaire and no group
        ODMBuilder.addItemGroupDef(new AttributePair("OID","IG_0000"),
                        new AttributePair("Name","Master group"),
                        new AttributePair("Mandatory","Yes")
                        );   
        ODMBuilder.updateItemGroupDefDescription(ODMGroupsCount-1, "Master group");
       
        //Loop through all itemComponents of the Questionnaire
        for(Questionnaire.QuestionnaireItemComponent itemComponent:itemList)
        {
            
        //Checking if the itemComponent has child Items -> create ItemGroup
            if(itemComponent.hasItem()){
                ODMGroupsCount++;                              
                ODMBuilder.addItemGroupDef(new AttributePair("OID",itemComponent.getLinkId()),
                        new AttributePair("Name",(itemComponent.getText()==null)?"No text":itemComponent.getText()),
                        new AttributePair("Mandatory",itemComponent.getRequired()?"Yes":"No")
                        );   
                ODMBuilder.updateItemGroupDefDescription(ODMGroupsCount-1, (itemComponent.getText()==null)?"":itemComponent.getText());
                
        //Get child items and loop through them            
                    subItemList = itemComponent.getItem();
                    for(Questionnaire.QuestionnaireItemComponent subItemComponent:subItemList)
                    {
        //If the child item has cild items the structure needs to be made (DE: flach)
        //We will call this proccess with its english equivalent flat/flatten (look method flattenItemList)
                        if(subItemComponent.hasItem()){
                        subSubItemList = flattenItemList(subItemComponent.getItem());
        //Now loop through all items flattened in a single list (Note that hasItem() property will be kept)
        //We will use hasItem() just to know if the item was parent(No ItemDef, it will just be added to the Group Description)
                        for(Questionnaire.QuestionnaireItemComponent subSubItemComponent:subSubItemList)
                        {
                        
                            if(subSubItemComponent.hasItem()&& !subSubItemComponent.hasAnswerOption()){
                        //No ItemDef just add text to Description of the group
                            ODMBuilder.updateItemGroupDefDescription(ODMGroupsCount-1, (subSubItemComponent.getText()==null)?"":subSubItemComponent.getText());
                            }else{
                        //Here come items with no children -> ItemDef will be created according to the ConversionLogic (look up method)
                            ODMItemCount++;                       
                            ConversionLogic(subSubItemComponent);
                            ODMBuilder.addItemRef(ODMGroupsCount-1, new AttributePair("ItemOID",subSubItemComponent.getLinkId()),
                                new AttributePair("Mandatory",subSubItemComponent.getRequired()?"Yes":"No")); 
                            }
                        }
        //If item has no children just add ItemDef
                        }else{
                            ODMItemCount++;               
                            ConversionLogic(subItemComponent);
                            ODMBuilder.addItemRef(ODMGroupsCount-1, new AttributePair("ItemOID",subItemComponent.getLinkId()),
                                new AttributePair("Mandatory",subItemComponent.getRequired()?"Yes":"No"));
                        }
                    }

                }else{             
        //Case where itemComponent belong to no group and will be appended to the Master groupd (default)
        
                    ConversionLogic(itemComponent);
                    ODMBuilder.addItemRef(0, new AttributePair("ItemOID",itemComponent.getLinkId()),
                    new AttributePair("Mandatory",itemComponent.getRequired()?"Yes":"No"));
                }    
                }
            
        
        //returning a built ODM File from the odm builder
           return this.ODMBuilder.build();
           
    }
    
    }