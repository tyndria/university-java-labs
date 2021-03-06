package wedding.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.xml.sax.SAXException;
import wedding.models.Person;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.*;

/**
 * Created by Antonina on 6/18/2017.
 */
public class StaxParser {
    private XMLEventWriter xmlEventWriter;
    private XMLEventReader xmlEventReader;
    private XMLEventFactory eventFactory;
    private XMLEvent end;

    private ArrayList<Integer> ids = new ArrayList<>();

    public StaxParser() {
        eventFactory = XMLEventFactory.newInstance();
        end = eventFactory.createDTD("\n");
    }

    public void write(String fileName, ArrayList<Person> people) {
        try {
            XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
            xmlEventWriter = xmlOutputFactory
                    .createXMLEventWriter(new FileOutputStream(new File(fileName)), "UTF-8");

            String rootElement = "people";
            XMLEventFactory eventFactory = XMLEventFactory.newInstance();

            XMLEvent end = eventFactory.createDTD("\n");

            xmlEventWriter.add(eventFactory.createStartDocument());
            xmlEventWriter.add(end);

            addStartElement(rootElement);

            for(Person person: people) {
                addElement(person);
            }

            addEndElement(rootElement);

            xmlEventWriter.add(eventFactory.createEndDocument());
            xmlEventWriter.close();
        } catch (XMLStreamException |FileNotFoundException e) {
            System.out.print(e.getClass() + ": " + e.getCause());
        }
    }

    public ArrayList<Person> read(String fileName) {
        ArrayList<Person> people = new ArrayList<>();
        Person person = null;
        ArrayList<String> propositions = null, demands = null;
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

        if (!validateXMLSchema("people.xsd", fileName)) {
            return people;
        }

        try {
            xmlEventReader = xmlInputFactory.createXMLEventReader(new FileInputStream(fileName));
            while(xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.nextEvent();

                if(xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();

                    if(startElement.getName().getLocalPart().equals("person")) {
                        person = new Person();
                        Attribute idAttr = startElement.getAttributeByName(new QName("id"));
                        if(idAttr != null){
                            Integer id = Integer.parseInt(idAttr.getValue());
                            person.setId(id);
                            ids.add(id);
                        }
                    } else if(startElement.getName().getLocalPart().equals("birthYear")) {
                        person.setBirthYear(getEventData());
                    } else if(startElement.getName().getLocalPart().equals("name")) {
                        person.setName(getEventData());
                    } else if(startElement.getName().getLocalPart().equals("surname")) {
                        person.setSurname(getEventData());
                    } else if(startElement.getName().getLocalPart().equals("propositions")) {
                        propositions = new ArrayList<>();
                    } else if(startElement.getName().getLocalPart().equals("demands")) {
                        demands = new ArrayList<>();
                    } else if (startElement.getName().getLocalPart().equals("demand")) {
                        if (demands != null) {
                            demands.add(getEventData());
                        }
                    } else if (startElement.getName().getLocalPart().equals("proposition")) {
                        if (propositions != null) {
                            propositions.add(getEventData());
                        }
                    }
                } else if(xmlEvent.isEndElement()) {
                    EndElement endElement = xmlEvent.asEndElement();
                    if (endElement.getName().getLocalPart().equals("person")) {
                       person.setPropositions(propositions);
                       person.setDemands(demands);
                       people.add(person);

                       propositions = null;
                       demands = null;
                       person = null;
                    }
                }
            }
        } catch (XMLStreamException |FileNotFoundException e) {
            System.out.print(e.getClass() + ": " + e.getCause());
        }

        return people;
    }

    private String getEventData() throws XMLStreamException{
        XMLEvent xmlEvent = xmlEventReader.nextEvent();
        return xmlEvent.asCharacters().getData();
    }

    public void create(String fileName, Person person) {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        try {
            xmlEventReader = xmlInputFactory.createXMLEventReader(new FileReader(fileName));
            xmlEventWriter = xmlOutputFactory.createXMLEventWriter(new FileWriter(fileName));
            while(xmlEventReader.hasNext()) {
                XMLEvent event = xmlEventReader.nextEvent();
                xmlEventWriter.add(event);
                if (event.getEventType() == XMLEvent.START_ELEMENT) {
                    if (event.asStartElement().getName().toString().equals("people")) {
                        addElement(person);
                    }
                }
            }
            xmlEventWriter.close();
        } catch (XMLStreamException | IOException e) {
            System.out.print(e.getClass() + ": " + e.getCause());
        }
    }

    public void delete(String fileName, String id) {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

        boolean deleteSection = false;
        try {
            xmlEventReader = xmlInputFactory.createXMLEventReader(new FileReader(fileName));
            xmlEventWriter = xmlOutputFactory.createXMLEventWriter(new FileWriter(fileName));
            while(xmlEventReader.hasNext()) {
                XMLEvent event = xmlEventReader.nextEvent();

                if (event.getEventType() == XMLEvent.START_ELEMENT &&
                        event.asStartElement().getName().toString().equals("person")) {
                    StartElement startElement  = event.asStartElement();
                    Attribute elementId = startElement.getAttributeByName(new QName("id"));
                    if (elementId.getValue().equals(id)) {
                        deleteSection = true;
                    } else {
                        xmlEventWriter.add(event);
                    }
                } else if (event.getEventType() == XMLEvent.END_ELEMENT &&
                        event.asEndElement().getName().toString().equals("person")) {
                    if (!deleteSection) {
                        xmlEventWriter.add(event);
                    } else {
                        deleteSection = false;
                    }
                } else if (!deleteSection) {
                    xmlEventWriter.add(event);
                }
            }
            xmlEventWriter.close();
        } catch (XMLStreamException | IOException e) {
            System.out.print(e.getClass() + ": " + e.getCause());
        }
    }

    private Integer getNextId() {
        int maxCurrentId = Collections.max(ids);
        ids.add(++maxCurrentId);
        return maxCurrentId;
    }

    private void addElement(Person person) throws XMLStreamException{
        String elementName = "person";

        xmlEventWriter.add(end);
        xmlEventWriter.add(eventFactory.createStartElement("", "", elementName));
        xmlEventWriter.add(eventFactory.createAttribute("id", getNextId() + ""));
        xmlEventWriter.add(end);

        Map<String, Object> objectMap = convertObjectToMap(person);
        Set<String> elementNodes = objectMap.keySet();
        for(String key : elementNodes){
            ArrayList<String> values = getValues(key, objectMap);
            XMLEvent tab = eventFactory.createDTD("\t");
            if (!key.equals("id")) {
                addNode(key, values, tab);
            }
        }

        addEndElement(elementName);
    }

    private void addNode(String key, ArrayList<String> values, XMLEvent tab) throws XMLStreamException{
        if (values.size() > 1 || key.equals("propositions") || key.equals("demands")) {
            XMLEvent doubleTab = eventFactory.createDTD("\t \t");
            xmlEventWriter.add(tab);
            addStartElement(key);
            for(String value: values) {
                ArrayList<String> bufArray = new ArrayList<>();
                bufArray.add(value);

                String singularFormKey = key.substring(0, key.length() - 1);
                addNode(singularFormKey, new ArrayList<>(bufArray), doubleTab);
            }
            xmlEventWriter.add(tab);
            addEndElement(key);
        }  else {
            xmlEventWriter.add(tab);
            xmlEventWriter.add(eventFactory.createStartElement("", "", key));
            Characters characters = eventFactory.createCharacters(values.get(0));
            xmlEventWriter.add(characters);

            addEndElement(key);
        }
    }

    private void addStartElement(String elementName) throws  XMLStreamException{
        xmlEventWriter.add(eventFactory.createStartElement("", "", elementName));
        xmlEventWriter.add(end);
    }

    private void addEndElement(String elementName) throws XMLStreamException {
        xmlEventWriter.add(eventFactory.createEndElement("", "", elementName));
        xmlEventWriter.add(end);
    }

    private Map<String, Object> convertObjectToMap(Person person) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.convertValue(person, Map.class);
    }

    private ArrayList<String> getValues(String key, Map<String, Object> map) {
        ArrayList<String> values = new ArrayList<>();
        Object value = map.get(key);
        if (value instanceof String) {
            values.add((String)value);
        } else if (value instanceof Integer) {
            values.add(value + "");
        } else if (value instanceof ArrayList) {
            values = (ArrayList<String>)map.get(key);
        }
        return values;
    }

    private boolean validateXMLSchema(String xsdPath, String xmlPath){
        try {
            SchemaFactory factory =
                    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new File(xmlPath)));
        } catch (IOException e) {
            System.out.println("Exception: " + e.getMessage());
            return false;
        } catch (SAXException e1) {
            System.out.println("SAX Exception: " + e1.getMessage());
            return false;
        }

        return true;
    }
}
