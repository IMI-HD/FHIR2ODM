package com.nkimi.fhir2odm;

import java.util.ArrayList;
import java.util.List;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;

/**
 *
 * @author Niko
 * This is a simple class to support the building of a ODM File from FHIR Questionnaire
 * Full functionality will not be supported
 */
public class ODMFileBuilder {
        private final Element root;
        private final List<Element> ItemDefList;
        private final List<Element> ItemGroupDefList;
        private final List<Element> CodeListList;
        private final Element FormDef;
        private final String title;
        private  Element ItemGroupRef,ItemDef,ItemRef,CodeList,ItemGroupDef;
        
    public ODMFileBuilder(String title) {
        this.root = new Element("ODM");       
        root.addAttribute(new Attribute("SourceSystem","FHIR2ODMConverter"));
        root.addAttribute(new Attribute("ODMVersion","1.3"));
        root.addAttribute(new Attribute("CreationDateTime",java.time.LocalDate.now().toString()));
        root.addAttribute(new Attribute("Description",title));    
        this.FormDef = new Element("FormDef");
        this.title=(title==null)?"No Title":title;
        this.ItemDefList = new ArrayList<>();
        this.ItemGroupDefList = new ArrayList<>();
        this.CodeListList = new ArrayList<>();
    }

    //This is a method for addding ItemDef to the ItemDefList
    //returns the index in the List
    public Integer addItemDef(AttributePair... pairs) {
        ItemDef = new Element("ItemDef");
        for(AttributePair pair:pairs)
        {
            ItemDef.addAttribute(new Attribute(pair.getKey(),pair.getValue()));
        }  
        this.ItemDefList.add(ItemDef);
        return ItemDefList.size()-1;
    }
    
    //This is a method for addding an Element to a ItemDef in the ItemDefList
    //Needs index as parameter
    public void addElementToItemDef(Integer index, Element e){
        if(index>=0 && index<=ItemDefList.size()){
            ItemDefList.get(index).appendChild(e);
        }  
    }
    
    //This is a method for addding an Attribute to a ItemDef in the ItemDefList
    //Needs index as parameter
    public void addAttributesToItemDef(Integer index, AttributePair... pairs){
        if(index>=0 && index<=ItemDefList.size()){
            for(AttributePair pair:pairs)
            {
                ItemDefList.get(index).addAttribute(new Attribute(pair.getKey(),pair.getValue()));
            }    
        }
    }
    
    //This is a method for addding CodeList to the CodeListList
    //returns the index in the List
    public Integer addCodeList(AttributePair... pairs) {
        CodeList = new Element("CodeList");
        for(AttributePair pair:pairs)
        {
            CodeList.addAttribute(new Attribute(pair.getKey(),pair.getValue()));
        }  
        this.CodeListList.add(CodeList);
        return CodeListList.size()-1;
    }
    
    //This is a method for addding an Element to a CodeList in the CodeListList
    //Needs index as parameter
    public void addElementToCodeListList(Integer index, Element e){
        if(index>=0 && index<=CodeListList.size()){
            CodeListList.get(index).appendChild(e);
        }
        
    }

    //This is a method for addding an  ItemGroupDef in the ItemGroupDefList
    //Attribute definition pairs as parameters
    public void addItemGroupDef(AttributePair... pairs) {
        ItemGroupDef = new Element("ItemGroupDef");
        Element Description = new Element("Description");
        Element TranslatedText = new Element("TranslatedText");
        
        //Default language en
        TranslatedText.addNamespaceDeclaration("xml", "http://www.w3.org/XML/1998/namespace");
        TranslatedText.addAttribute(new Attribute("xml:lang","http://www.w3.org/XML/1998/namespace","en"));
        Description.appendChild(TranslatedText);
        ItemGroupDef.appendChild(Description);
        for(AttributePair pair:pairs)
        {
            ItemGroupDef.addAttribute(new Attribute(pair.getKey(),pair.getValue()));
        }                
        this.ItemGroupDefList.add(ItemGroupDef);
    }
    
    //This is a method for updating the Description of ItemGroupDef
    //Needs index as parmaeter
    public void updateItemGroupDefDescription(Integer index, String text){
        if(index>=0 && index<=ItemGroupDefList.size()){
            ItemGroupDefList.get(index).getFirstChildElement("Description").getFirstChildElement("TranslatedText").appendChild(text+" / ");
        }
    }
    
    //This is a method for addding an  ItemRef to a ItemGroupDef in the ItemGroupDefList
    //Needs index as parameter
    public void addItemRef(Integer index, AttributePair... pairs) {
       if(index>=0 && index<=ItemGroupDefList.size()){
        ItemRef = new Element("ItemRef");
        for(AttributePair pair:pairs)
        {
            ItemRef.addAttribute(new Attribute(pair.getKey(),pair.getValue()));
        }   
        ItemGroupDefList.get(index).appendChild(ItemRef);
        }
    }
    
    //This is a method which builds the ODM File and returns a XML Document
    public Document build(){
        
        Element Study = new Element("Study");
        Study.addAttribute(new Attribute("OID","S.0000"));
        
        Element MetaDataVersion = new Element("MetaDataVersion");
        MetaDataVersion.addAttribute(new Attribute("OID","MD.0000"));
        MetaDataVersion.addAttribute(new Attribute("Name","Metadataversion"));
        //Here appen Protocol
        //Here append StudyeventDef
        
        this.FormDef.addAttribute(new Attribute("OID","F.0000"));
        this.FormDef.addAttribute(new Attribute("Name",this.title));
        this.FormDef.addAttribute(new Attribute("Repeating","No"));
        //Here code for FormDef ... ItemGroupRef
        //MetaDataVersion.appendChild(Protocol);
        //MetaDataVersion.appendChild(StudyEventDef);
        MetaDataVersion.appendChild(this.FormDef);
        //MetaDataVersion.appendChild(ItemGroupDef);
        
        //Building the ItemGroupDefList and adding it to MetaDataVersion
        //First check to see if the default group has children and if not.. destroy it
        if(ItemGroupDefList.get(0).getChildElements().size()==1){
            ItemGroupDefList.remove(0);
        }
        for(Element ItemGroupDefElement:ItemGroupDefList ){
        MetaDataVersion.appendChild(ItemGroupDefElement);
        //Adding the ItemGroupRef to FormDef
        ItemGroupRef = new Element("ItemGroupRef");
        String ItemGroupRefOID = ItemGroupDefElement.getAttribute("OID").getValue();
        Attribute ItemGroupMendatory = ItemGroupDefElement.getAttribute("Mandatory");
        ItemGroupDefElement.removeAttribute(ItemGroupMendatory);
        ItemGroupRef.addAttribute(new Attribute("ItemGroupOID",ItemGroupRefOID)); 
        ItemGroupRef.addAttribute(new Attribute("Mandatory",ItemGroupMendatory.getValue())); 
        FormDef.appendChild(ItemGroupRef);
        }
        
        //Loop through ItemDefList and add it to MetaDataVersion
        for(Element ItemDefElement:ItemDefList ){
        MetaDataVersion.appendChild(ItemDefElement);
        }
        for(Element CodeListElement:CodeListList ){
        MetaDataVersion.appendChild(CodeListElement);
        }
        Study.appendChild(MetaDataVersion);
        root.appendChild(Study);
        return new Document(root);
    }
        
}
