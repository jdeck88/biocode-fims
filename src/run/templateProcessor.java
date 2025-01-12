package run;

import digester.*;
import org.apache.commons.digester3.Digester;
import org.apache.poi.hssf.util.CellReference;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import settings.FIMSRuntimeException;
import settings.PathManager;
import settings.bcidConnector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * This is a convenience class for working with templates (the spreadsheet generator).
 * We handle building working with the "process" class and the digester rules for mapping & fims,
 * in addition to providing methods for looking up definitions, and building form output
 */
public class templateProcessor {

    private process p;
    private Mapping mapping;
    private Fims fims;
    private Validation validation;
    private Integer accessionNumber;
    private String datasetCode;
    private Integer naan;

    private String ark;

    private static Logger logger = LoggerFactory.getLogger(templateProcessor.class);

    XSSFSheet defaultSheet;
    XSSFWorkbook workbook;

    XSSFCellStyle headingStyle, regularStyle, requiredStyle, wrapStyle;

    final int NAME = 0;
    final int DEFINITION = 1;
    final int CONTROLLED_VOCABULARY = 2;
    final int DATA_FORMAT = 3;
    final int SYNONYMS = 4;


    String instructionsSheetName = "Instructions";
    String dataFieldsSheetName = "Data Fields";
    String listsSheetName = "Lists";

    private static String warningMsg = "The value you entered is not from the recommended list. This will create a warning upon validation.";
    private static String errorMsg = "The value you entered is not from the recommended list. This will create an error upon validation.";

    static File configFile = null;
    Integer project_id;
    private String username = null;

    public templateProcessor(Integer project_id, String outputFolder, Boolean useCache, XSSFWorkbook workbook) {
        this.project_id = project_id;
        configurationFileFetcher configFile = new configurationFileFetcher(project_id, outputFolder, useCache);
        bcidConnector bcidConnector = new bcidConnector();
        naan = bcidConnector.getNAAN();

        // Instantiate the project output Folder
        this.p = new process(project_id,outputFolder, configFile.getOutputFile());
        mapping = new Mapping();
        p.addMappingRules(new Digester(), mapping);

        fims = new Fims(mapping, null);
        p.addFimsRules(new Digester(), fims);

        validation = new Validation();
        p.addValidationRules(new Digester(), validation);
        this.workbook = (XSSFWorkbook) workbook;
        // Set the default heading style
        headingStyle = workbook.createCellStyle();
        XSSFFont bold = workbook.createFont();
        bold.setFontHeightInPoints((short) 14);
        bold.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
        headingStyle.setFont(bold);


        requiredStyle = workbook.createCellStyle();
        XSSFFont redBold = workbook.createFont();
        redBold.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
        redBold.setFontHeightInPoints((short) 14);
        redBold.setColor(XSSFFont.COLOR_RED);
        requiredStyle.setFont(redBold);

        wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);

        // Set the style for all other cells
        regularStyle = workbook.createCellStyle();

    }

    /**
     * Instantiate tempalateProcessor using a pre-defined configurationFile (don't fetch using projectID)
     * This is a private constructor as it is ONLY used for local testing.  Do not use on the Web or in production
     * since we MUST know the project_id first
     *
     * @param file
     * @param outputFolder
     * @param useCache
     */
    private void instantiateTemplateProcessor(File file, String outputFolder, Boolean useCache) {
        configFile = file;

        bcidConnector bcidConnector = new bcidConnector();
        naan = bcidConnector.getNAAN();

        // Instantiate the project output Folder
        this.p = new process(project_id,outputFolder, configFile);

        mapping = new Mapping();
        p.addMappingRules(new Digester(), mapping);

        fims = new Fims(mapping, null);
        p.addFimsRules(new Digester(), fims);

        validation = new Validation();
        p.addValidationRules(new Digester(), validation);

        // Create the workbook
        workbook = new XSSFWorkbook();

        // Set the default heading style
        headingStyle = workbook.createCellStyle();
        XSSFFont bold = workbook.createFont();
        bold.setFontHeightInPoints((short) 14);
        bold.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
        headingStyle.setFont(bold);


        requiredStyle = workbook.createCellStyle();
        XSSFFont redBold = workbook.createFont();
        redBold.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
        redBold.setFontHeightInPoints((short) 14);
        redBold.setColor(XSSFFont.COLOR_RED);
        requiredStyle.setFont(redBold);

        wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);

        // Set the style for all other cells
        regularStyle = workbook.createCellStyle();
    }

    /**
     * Instantiate templateProcessor using a project ID (lookup configuration File from server)
     *
     * @param project_id
     * @param outputFolder
     * @param useCache
     */
    public void instantiateTemplateProcessor(Integer project_id, String outputFolder, Boolean useCache) {
        this.project_id = project_id;
        configurationFileFetcher fetcher = new configurationFileFetcher(project_id, outputFolder, useCache);
        instantiateTemplateProcessor(fetcher.getOutputFile(), outputFolder, useCache);
    }


    /**
     * constructor for NMNH projects
     *
     * @param project_id
     * @param outputFolder
     * @param useCache
     * @param accessionNumber
     * @param datasetCode
     */
    public templateProcessor(Integer project_id, String outputFolder, Boolean useCache,
                             Integer accessionNumber, String datasetCode, String ark, String username) {
        // we can't have a null value for accessionNumber or datasetCode if using this constructor
        if (accessionNumber == null || datasetCode == null) {
            throw new FIMSRuntimeException("dataset code and accession number are required", 500);
        }
        this.username = username;
        this.accessionNumber = accessionNumber;
        this.datasetCode = datasetCode;
        this.ark = ark;
        instantiateTemplateProcessor(project_id, outputFolder, useCache);
    }

    /**
     * constructor for NMNH projects
     *
     * @param file
     * @param outputFolder
     * @param useCache
     * @param accessionNumber
     * @param datasetCode
     */
    public templateProcessor(File file, String outputFolder, Boolean useCache,
                             Integer accessionNumber, String datasetCode, String ark) {
        // we can't have a null value for accessionNumber or datasetCode if using this constructor
        if (accessionNumber == null || datasetCode == null) {
            throw new FIMSRuntimeException("dataset code and accession number are required", 500);
        }
        this.accessionNumber = accessionNumber;
        this.datasetCode = datasetCode;
        this.ark = ark;
        instantiateTemplateProcessor(file, outputFolder, useCache);
    }

    public templateProcessor(Integer project_id, String outputFolder, Boolean useCache) {
        instantiateTemplateProcessor(project_id, outputFolder, useCache);
    }

    public Mapping getMapping() {
        return mapping;
    }

    public Fims getFims() {
        return fims;
    }

    public Validation getValidation() {
        return validation;
    }

    /**
     * Get a definition for a particular column name
     *
     * @param column_name
     *
     * @return
     */
    public String definition(String column_name) {
        StringBuilder output = new StringBuilder();

        Iterator attributes = mapping.getAllAttributes(mapping.getDefaultSheetName()).iterator();
        // Get a list of rules for the first digester.Worksheet instance
        Worksheet sheet = this.validation.getWorksheets().get(0);

        List<Rule> rules = sheet.getRules();


        while (attributes.hasNext()) {
            Attribute a = (Attribute) attributes.next();
            String column = a.getColumn();
            if (column_name.trim().equals(column.trim())) {
                // The column name
                output.append("<b>Column Name: " + column_name + "</b><p>");

                // URI
                if (a.getUri() != null) {
                    output.append("URI = " +
                            "<a href='" + a.getUri() + "' target='_blank'>" +
                            a.getUri() +
                            "</a><br>\n");
                }
                // Defined_by
                if (a.getDefined_by() != null) {
                    output.append("Defined_by = " +
                            "<a href='" + a.getDefined_by() + "' target='_blank'>" +
                            a.getDefined_by() +
                            "</a><br>\n");
                }

                // Definition
                if (a.getDefinition() != null && !a.getDefinition().trim().equals("")) {
                    output.append("<p>\n" +
                            "<b>Definition:</b>\n" +
                            "<p>" + a.getDefinition() + "\n");
                } else {
                    output.append("<p>\n" +
                            "<b>Definition:</b>\n" +
                            "<p>No custom definition available\n");
                }

                // Synonyms
                if (a.getSynonyms() != null && !a.getSynonyms().trim().equals("")) {
                    output.append("<p>\n" +
                            "<b>Synonyms:</b>\n" +
                            "<p>" + a.getSynonyms() + "\n");
                }

                // Synonyms
                if (a.getDataFormat() != null && !a.getDataFormat().trim().equals("")) {
                    output.append("<p>\n" +
                            "<b>Data Formatting Instructions:</b>\n" +
                            "<p>" + a.getDataFormat() + "\n");
                }

                // Rules
                Iterator it = rules.iterator();
                StringBuilder ruleValidations = new StringBuilder();
                while (it.hasNext()) {

                    Rule r = (Rule) it.next();
                    r.setDigesterWorksheet(sheet);

                    if (r != null) {
                        digester.List sList = validation.findList(r.getList());

                        // Convert to native state (without underscores)
                        String ruleColumn = r.getColumn();

                        if (ruleColumn != null) {
                            // Match column names with or without underscores
                            if (ruleColumn.replace("_", " ").equals(column) ||
                                    ruleColumn.equals(column)) {
                                ruleValidations.append(r.printRuleMetadata(sList));
                            }
                        }
                    }
                }
                if (!ruleValidations.toString().equals("")) {
                    output.append("<p>\n" +
                            "<b>Validation Rules:</b>\n<p>");
                    output.append(ruleValidations.toString());
                }

                return output.toString();
            }
        }

        return "No definition found for " + column_name;
    }

    /**
     * Generate checkBoxes/Column Names for the mappings in a template
     *
     * @return
     */
    public String printCheckboxes() {
        LinkedList<String> requiredColumns = getRequiredColumns("error");
        LinkedList<String> desiredColumns = getRequiredColumns("warning");
        // Use TreeMap for natural sorting of groups
        Map<String, StringBuilder> groups = new TreeMap<String, StringBuilder>();

        //StringBuilder output = new StringBuilder();
        // A list of names we've already added
        ArrayList addedNames = new ArrayList();
        Iterator attributes = mapping.getAllAttributes(mapping.getDefaultSheetName()).iterator();
        while (attributes.hasNext()) {
            Attribute a = (Attribute) attributes.next();

            StringBuilder thisOutput = new StringBuilder();
            // Set the column name
            String column = a.getColumn();
            String group = a.getGroup();
            String uri = a.getUri();

            // Check that this name hasn't been read already.  This is necessary in some situations where
            // column names are repeated for different entities in the configuration file
            if (!addedNames.contains(column)) {
                // Set boolean to tell us if this is a requiredColumn
                Boolean aRequiredColumn = false, aDesiredColumn = false;
                if (requiredColumns == null) {
                    aRequiredColumn = false;
                } else if (requiredColumns.contains(a.getColumn())) {
                    aRequiredColumn = true;
                }
                if (desiredColumns == null) {
                    aDesiredColumn = false;
                } else if (desiredColumns.contains(a.getColumn())) {
                    aDesiredColumn = true;
                }


                // Construct the checkbox text
                thisOutput.append("<input type='checkbox' class='check_boxes' value='" + column + "' data-uri='");
                thisOutput.append(uri);
                thisOutput.append("'");

                // If this is a required column then make it checked (and immutable)
                if (aRequiredColumn)
                    thisOutput.append(" checked disabled");
                else if (aDesiredColumn)
                    thisOutput.append(" checked");

                // Close tag and insert Definition link
                thisOutput.append(">" + column + " \n" +
                        "<a href='#' class='def_link' name='" + column + "'>DEF</a>\n" + "<br>\n");

                // Fetch any existing content for this key
                if (group == null || group.equals("")) {
                    group = "Default Group";
                }
                StringBuilder existing = groups.get(group);

                // Append (not required) or Insert (required) the new content onto any existing in this key
                if (existing == null) {
                    existing = thisOutput;
                } else {
                    if (aRequiredColumn) {
                        existing.insert(0, thisOutput);
                    } else {
                        existing.append(thisOutput);
                    }
                }
                groups.put(group, existing);

                //groups.put(group, existing == null ? thisOutput : existing.append(thisOutput));

            }

            // Now that we've added this to the output, add it to the ArrayList so we don't add it again
            addedNames.add(column);
        }

        // Iterate through any defined groups, which makes the template processor easier to navigate
        Iterator it = groups.entrySet().iterator();
        StringBuilder output = new StringBuilder();
        output.append("<a href='#' id='select_all'>Select ALL</a> | ");
        output.append("<a href='#' id='select_none'>Select NONE</a> | ");
        output.append("<a href='#' onclick='saveTemplateConfig()'>Save</a>");
        output.append("<script>" +
                "$('#select_all').click(function(event) {\n" +
                "      // Iterate each checkbox\n" +
                "      $(':checkbox').each(function() {\n" +
                "          this.checked = true;\n" +
                "      });\n" +
                "  });\n" +
                "$('#select_none').click(function(event) {\n" +
                "    $(':checkbox').each(function() {\n" +
                "       if (!$(this).is(':disabled')) {\n" +
                "          this.checked = false;}\n" +
                "      });\n" +
                "});" +
                "</script>");

        int count = 0;
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            String groupName;

            try {
                groupName = pairs.getKey().toString();
            } catch (NullPointerException e) {
                groupName = "Default Group";
            }
            if (groupName.equals("") || groupName.equals("null")) {
                groupName = "Default Group";
            }

            // Anchors cannot have spaces in the name so we replace them with underscores
            String massagedGroupName = groupName.replaceAll(" ", "_");
            if (!pairs.getValue().toString().equals("")) {
                output.append("<div class=\"panel panel-default\">");
                output.append("<div class=\"panel-heading\"> " +
                        "<h4 class=\"panel-title\"> " +
                        "<a class=\"accordion-toggle\" data-toggle=\"collapse\" data-parent=\"#accordion\" href=\"#" + massagedGroupName + "\">" + groupName + "</a> " +
                        "</h4> " +
                        "</div>");
                output.append("<div id=\"" + massagedGroupName + "\" class=\"panel-collapse collapse");
                // Make the first element open initially
                if (count == 0) {
                    output.append(" in");
                }
                output.append("\">\n" +
                        "                <div class=\"panel-body\">\n" +
                        "                    <div id=\"" + massagedGroupName + "\" class=\"panel-collapse collapse in\">");
                output.append(pairs.getValue().toString());
                output.append("\n</div></div></div></div>");
            }

            it.remove(); // avoids a ConcurrentModificationException
            count++;
        }
        return output.toString();
    }

    /**
     * This function creates a sheet called "Lists" and then creates the pertinent validations for each of the lists
     *
     * @param fields
     */

    private void createListsSheetAndValidations(List<String> fields) {
        // Integer for holding column index value
        int column;
        // Create a sheet to hold the lists
        XSSFSheet listsSheet = workbook.createSheet(listsSheetName);

        // An iterator of the possible lists
        Iterator listsIt = validation.getLists().iterator();

        // Track which column number we're looking at
        int listColumnNumber = 0;

        // Loop our array of lists
        while (listsIt.hasNext()) {
            // Get an instance of a particular list
            digester.List list = (digester.List) listsIt.next();

            //Get the number of rows in this list
            int numRowsInList = list.getFields().size();

            // List of fields from this validation rule
            java.util.List validationFieldList = list.getFields();

            // Validation Fields
            if (validationFieldList.size() > 0) {

                // populate this validation list in the Lists sheet
                int counterForRows = 0;
                Iterator fieldlistIt = validationFieldList.iterator();
                while (fieldlistIt.hasNext()) {
                    String value;
                    XSSFCellStyle style;
                    // Write header
                    if (counterForRows == 0) {
                        value = list.getAlias();
                        style = headingStyle;
                        XSSFRow row = listsSheet.getRow(counterForRows);
                        if (row == null)
                            row = listsSheet.createRow(counterForRows);

                        XSSFCell cell = row.createCell(listColumnNumber);
                        cell.setCellValue(value);
                        cell.setCellStyle(style);
                    }
                    // Write cell values
                    Field f = (Field) fieldlistIt.next();
                    value = f.getValue();

                    style = regularStyle;

                    // Set the row counter to +1 because of the header issues
                    counterForRows++;
                    XSSFRow row = listsSheet.getRow(counterForRows);
                    if (row == null)
                        row = listsSheet.createRow(counterForRows);

                    XSSFCell cell = row.createCell(listColumnNumber);
                    cell.setCellValue(value);
                    cell.setCellStyle(style);

                }

                // Autosize this column
                listsSheet.autoSizeColumn(listColumnNumber);

                // Get the letter of this column
                String listColumnLetter = CellReference.convertNumToColString(listColumnNumber);

                // Figure out the last row number
                int endRowNum = numRowsInList + 1;

                // DATA VALIDATION COMPONENT
                // TODO: expand this to select the appropriate worksheet but for NOW there is only ONE so just get last
                Worksheet validationWorksheet = validation.getWorksheets().getLast();
                // An arrayList of columnNames in the default sheet that this list should be applied to
                ArrayList<String> columnNames = validationWorksheet.getColumnsForList(list.getAlias());

                // Determine if the list will throw a warning or an error message upon validation
                List<Rule> rules = validationWorksheet.getRules();
                Boolean errorLevel = false;
                Iterator rulesIt = rules.iterator();

                while (rulesIt.hasNext()) {
                    Rule r = (Rule) rulesIt.next();
                    if (r.getList() != null && r.getList().equalsIgnoreCase(list.getAlias())) {
                        if (r.getLevel().equalsIgnoreCase("warning")) {
                            errorLevel = true;
                        }
                    }
                }


                Iterator columnNamesIt = columnNames.iterator();
                // Loop all of the columnNames
                while (columnNamesIt.hasNext()) {
                    String thisColumnName = (String) columnNamesIt.next();
                    column = fields.indexOf(thisColumnName.replace("_", " "));
                    if (column >= 0) {
                        ///   CellRangeAddressList addressList = new CellRangeAddressList(1, 100000, 2, 2);

                        // Set the Constraint to a particular column on the lists sheet
                        // The following syntax works well and shows popup boxes: Lists!S:S
                        // replacing the previous syntax which does not show popup boxes ListsS
                        // Assumes that header is in column #1
                        String constraintSyntax = listsSheetName + "!$" + listColumnLetter + "$2:$" + listColumnLetter + "$" + endRowNum;

                        XSSFDataValidationHelper dvHelper =
                                new XSSFDataValidationHelper(listsSheet);

                        XSSFDataValidationConstraint dvConstraint =
                                (XSSFDataValidationConstraint) dvHelper.createFormulaListConstraint(constraintSyntax);

                        // This defines an address range for this particular list
                        CellRangeAddressList addressList = new CellRangeAddressList();
                        addressList.addCellRangeAddress(1, column, 50000, column);

                        XSSFDataValidation dataValidation =
                                (XSSFDataValidation) dvHelper.createValidation(dvConstraint, addressList);

                        // Data validation styling
                        dataValidation.setSuppressDropDownArrow(true);
                        dataValidation.setShowErrorBox(true);

                        // Give the user the appropriate data validation error msg, depending upon the rules error level
                        if (errorLevel) {
                            dataValidation.createErrorBox("Data Validation Error", errorMsg);
                            dataValidation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                        } else {
                            dataValidation.createErrorBox("Data Validation Warning", warningMsg);
                            dataValidation.setErrorStyle(DataValidation.ErrorStyle.INFO);
                        }

                        // Add the validation to the defaultsheet
                        defaultSheet.addValidationData(dataValidation);
                    }
                }
                listColumnNumber++;
            }
        }
    }


    /**
     * Find the required columns on this sheet
     *
     * @return
     */
    public LinkedList<String> getRequiredColumns(String level) {
        LinkedList<String> columnSet = new LinkedList<String>();
        Iterator worksheetsIt = validation.getWorksheets().iterator();
        while (worksheetsIt.hasNext()) {
            Worksheet w = (Worksheet) worksheetsIt.next();
            Iterator rIt = w.getRules().iterator();
            while (rIt.hasNext()) {
                Rule r = (Rule) rIt.next();
                //System.out.println(r.getType() + r.getColumn() + r.getFields());
                if (r.getType().equals("RequiredColumns") &&
                        r.getLevel().equals(level)) {
                    columnSet.addAll(r.getFields());
                }
            }
        }
        if (columnSet.size() < 1)
            return null;
        else
            return columnSet;
    }

    /**
     * Create the DataFields sheet
     */
    private void createDataFields(List<String> fields) {

        // Create the Instructions Sheet, which is always first
        XSSFSheet dataFieldsSheet = workbook.createSheet(dataFieldsSheetName);

        // First find all the required columns so we can look them up
        LinkedList<String> requiredColumns = getRequiredColumns("error");


        // Loop through all fields in schema and provide names, uris, and definitions
        //Iterator entitiesIt = getMapping().getEntities().iterator();
        Iterator fieldsIt = fields.iterator();
        int rowNum = 0;
        Row row = dataFieldsSheet.createRow(rowNum++);

        //XSSFCellStyle dataFieldStyle = headingStyle;
        //dataFieldStyle.setBorderBottom(BorderStyle.THIN);

        // HEADER ROWS
        Cell cell = row.createCell(NAME);
        cell.setCellStyle(headingStyle);
        cell.setCellValue("ColumnName");

        cell = row.createCell(DEFINITION);
        cell.setCellStyle(headingStyle);
        cell.setCellValue("Definition");

        cell = row.createCell(CONTROLLED_VOCABULARY);
        cell.setCellStyle(headingStyle);
        cell.setCellValue("Controlled Vocabulary (see Lists)");

        cell = row.createCell(DATA_FORMAT);
        cell.setCellStyle(headingStyle);
        cell.setCellValue("Data Format");

        cell = row.createCell(SYNONYMS);
        cell.setCellStyle(headingStyle);
        cell.setCellValue("Synonyms");

        // Must loop entities first
        while (fieldsIt.hasNext()) {
            // Generally, treat column Names with underscores or not, replacing spaces
            String columnName = fieldsIt.next().toString().replace("_", " ");
            LinkedList<Entity> entities = mapping.getEntities();
            Iterator entitiesIt = entities.iterator();
            while (entitiesIt.hasNext()) {
                digester.Entity e = (digester.Entity) entitiesIt.next();

                // Loop attributes
                Iterator attributesIt = ((LinkedList<Attribute>) e.getAttributes()).iterator();

                // Then loop attributes
                while (attributesIt.hasNext()) {

                    Attribute a = (Attribute) attributesIt.next();

                    // Some XML configuration files allow spaces in column Names... here we search for
                    // matching column names with or without spaces, replaced by underscores
                    if (a.getColumn().replace("_", " ").equals(columnName) ||
                            a.getColumn().equals(columnName)) {
                        row = dataFieldsSheet.createRow(rowNum++);

                        // Column Name
                        Cell nameCell = row.createCell(NAME);
                        nameCell.setCellValue(a.getColumn());
                        XSSFCellStyle nameStyle;
                        if (requiredColumns != null && requiredColumns.contains(a.getColumn())) {
                            nameStyle = requiredStyle;
                        } else {
                            nameStyle = headingStyle;
                        }
                        nameStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
                        nameCell.setCellStyle(nameStyle);

                        // Definition
                        Cell defCell = row.createCell(DEFINITION);
                        defCell.setCellValue(a.getDefinition());
                        defCell.setCellStyle(wrapStyle);

                        // Controlled Vocabulary
                        Worksheet sheet = this.validation.getWorksheets().get(0);
                        Iterator rulesIt = sheet.getRules().iterator();
                        while (rulesIt.hasNext()) {
                            digester.Rule r = (digester.Rule) rulesIt.next();
                            if (r.getColumn() != null &&
                                    r.getList() != null &&
                                    r.getColumn().replace("_", " ").equals(columnName) &&
                                    (r.getType().equals("controlledVocabulary") || r.getType().equals("checkInXMLFields"))) {
                                Cell controlledVocabCell = row.createCell(CONTROLLED_VOCABULARY);
                                controlledVocabCell.setCellValue(r.getList());
                                controlledVocabCell.setCellStyle(wrapStyle);
                            }
                        }

                        // Data Format
                        try {
                            Cell formatCell = row.createCell(DATA_FORMAT);
                            formatCell.setCellValue(a.getDataFormat());
                            formatCell.setCellStyle(wrapStyle);
                        } catch (NullPointerException npe) {
                            logger.warn("NullPointerException", npe);
                        }

                        // Synonyms
                        try {
                            Cell synonymCell = row.createCell(SYNONYMS);
                            synonymCell.setCellValue(a.getSynonyms());
                            synonymCell.setCellStyle(wrapStyle);
                        } catch (NullPointerException npe) {
                            logger.warn("NullPointerException", npe);
                        }
                    }
                }
            }
        }

        // Set column width
        dataFieldsSheet.setColumnWidth(NAME, 25 * 256);
        dataFieldsSheet.setColumnWidth(DEFINITION, 60 * 256);
        dataFieldsSheet.setColumnWidth(CONTROLLED_VOCABULARY, 25 * 256);
        dataFieldsSheet.setColumnWidth(DATA_FORMAT, 40 * 256);
        dataFieldsSheet.setColumnWidth(SYNONYMS, 40 * 256);

    }

    /**
     * Create the default Sheet
     *
     * @param defaultSheetname
     * @param fields
     */
    private void createDefaultSheet(String defaultSheetname, List<String> fields) {
        // Create the Default Sheet sheet
        defaultSheet = workbook.createSheet(defaultSheetname);

        //Create the header row
        XSSFRow row = defaultSheet.createRow(0);

        // First find all the required columns so we can look them up
        LinkedList<String> requiredColumns = getRequiredColumns("error");

        // Loop the fields that the user wants in the default sheet
        int columnNum = 0;
        Iterator itFields = fields.iterator();
        while (itFields.hasNext()) {
            String field = (String) itFields.next();
            Cell cell = row.createCell(columnNum++);
            //Set value to new value
            cell.setCellValue(field);
            cell.setCellStyle(headingStyle);

            // Make required columns red
            if (requiredColumns != null && requiredColumns.contains(field))
                cell.setCellStyle(requiredStyle);

        }

        // Auto-size the columns so we can see them all to begin with
        for (int i = 0; i <= columnNum; i++) {
            defaultSheet.autoSizeColumn(i);
        }

    }

    /**
     * Create an instructions sheet
     *
     * @param defaultSheetName
     */
    private void createInstructions(String defaultSheetName) {
        // Create the Instructions Sheet, which is always first
        XSSFSheet instructionsSheet = workbook.createSheet(instructionsSheetName);
        Row row;
        Cell cell;
        Integer rowIndex = 0;

        // Center align & bold for title
        XSSFCellStyle titleStyle = workbook.createCellStyle();
        XSSFFont bold = workbook.createFont();
        bold.setFontHeightInPoints((short) 14);

        bold.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
        titleStyle.setFont(bold);
        titleStyle.setAlignment(CellStyle.ALIGN_CENTER);

        // Make a big first column
        instructionsSheet.setColumnWidth(0, 160 * 256);

        //Fetch the project title from the BCID system
        // NOTE, getting this particular name from the BCID system throws a connection exception
        /* availableProjectsFetcher fetcher = new availableProjectsFetcher();
        availableProject aP = fetcher.getProject(project_id);
        String project_title = aP.getProject_title();
        */
        // Use the shortName
        String project_title = getFims().getMetadata().getShortname();

        // Hide the project_id in the first row
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        rowIndex++;

        // Hide NAAN in first row, first column
        cell = row.createCell(0);
        cell.setCellValue("~naan=" + naan + "~");

        // Hide Project_id in first row, second column
        cell = row.createCell(1);
        cell.setCellValue("~project_id=" + project_id + "~");

        row.setZeroHeight(true);

        // The name of this project as specified by the sheet
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(titleStyle);
        cell.setCellValue(project_title);

        // if we have a datasetCode and accesstionNumber, hide them in the first row and make them visible
        // if we have one, we have all three.
        if (accessionNumber != null) {
            // Hide the dataset_code in first row, third column
            row = instructionsSheet.getRow(0);
            cell = row.createCell(2);
            cell.setCellValue("~dataset_code=" + datasetCode + "~");

            // Hide the accession number in first row, fourth column
            cell = row.createCell(3);
            cell.setCellValue("~accesstion_number=" + accessionNumber + "~");

            // Show the datasetCode
            row = instructionsSheet.createRow(rowIndex);
            rowIndex++;
            cell = row.createCell(0);
            cell.setCellStyle(titleStyle);
            cell.setCellValue(formatKeyValueString("Dataset Code: ", datasetCode));

            // Show the ark
            if (ark != null && !ark.equals("")) {
                row = instructionsSheet.createRow(rowIndex);
                rowIndex++;
                cell = row.createCell(0);
                cell.setCellStyle(titleStyle);
                cell.setCellValue(formatKeyValueString("ARK root: ", ark));
            }

            // Show the Accession Number
            row = instructionsSheet.createRow(rowIndex);
            rowIndex++;
            cell = row.createCell(0);
            cell.setCellStyle(titleStyle);
            cell.setCellValue(formatKeyValueString("Accession Number: ", accessionNumber.toString()));
        }


        // Print todays date with user name
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(titleStyle);
        DateFormat dateFormat = new SimpleDateFormat("MMMMM dd, yyyy");
        Calendar cal = Calendar.getInstance();
        String dateAndUser = "Templated generated ";
        if (username != null && !username.trim().equals("")) {
            dateAndUser += "by '" + username + "' ";
        }
        dateAndUser += "on " + dateFormat.format(cal.getTime());
        cell.setCellValue(dateAndUser);

        // Prompt for data enterer name
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(titleStyle);
        cell.setCellValue("Person(s) responsible for data entry [                       ]");

        // Insert additional row before next content
        rowIndex++;

        // Default sheet instructions
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(headingStyle);
        cell.setCellValue(defaultSheetName + " Tab");

        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(wrapStyle);
        cell.setCellValue("Please fill out each field in the \"" + defaultSheetName + "\" tab as completely as possible. " +
                "Fields in red are required (data cannot be uploaded to the database without these fields). " +
                "Required and recommended fields are usually placed towards the beginning of the template. " +
                "Some fields have a controlled vocabulary associated with them in the \"" + listsSheetName + "\" tab " +
                "and are provided as data validation in the provided cells" +
                "If you have more than one entry to a field (i.e. a list of publications), " +
                "please delimit your list with pipes (|).  Also please make sure that there are no newline " +
                "characters (=carriage returns) in any of your metadata. Fields in the " + defaultSheetName + " tab may be re-arranged " +
                "in any order so long as you don't change the field names.");

        // data Fields sheet
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(headingStyle);
        cell.setCellValue(dataFieldsSheetName + " Tab");

        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(wrapStyle);
        cell.setCellValue("This tab contains column names, associated URIs and definitions for each column.");

        //Lists Tab
        row = instructionsSheet.createRow(rowIndex);
        rowIndex++;
        cell = row.createCell(0);
        cell.setCellStyle(headingStyle);
        cell.setCellValue(listsSheetName + " Tab");

        row = instructionsSheet.createRow(rowIndex);
        cell = row.createCell(0);
        cell.setCellStyle(wrapStyle);
        cell.setCellValue("This tab contains controlled vocabulary lists for certain fields.  DO NOT EDIT this sheet!");


        // Create a Box to Hold The Critical Information
        /*
        HSSFPatriarch patriarch = instructionsSheet.createDrawingPatriarch();
        HSSFClientAnchor a = new HSSFClientAnchor(0, 0, 1023, 255, (short) 1, 0, (short) 1, 0);
        HSSFSimpleShape shape1 = patriarch.createSimpleShape(a);
        shape1.setShapeType(HSSFSimpleShape.OBJECT_TYPE_LINE);

        // Create the textbox
        HSSFTextbox textbox = patriarch.createTextbox(
                new HSSFClientAnchor(0, 0, 0, 0, (short) 0, 3, (short) 1, 12));
        textbox.setHorizontalAlignment(CellStyle.ALIGN_CENTER);

        // Accession ID
        HSSFFont font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setBoldweight(Font.BOLDWEIGHT_BOLD);
        font.setFontHeightInPoints((short) 18);
        HSSFRichTextString accessionString = new HSSFRichTextString(accesstionNumber.toString());
        accessionString.applyFont(font);
        textbox.setString(accessionString);

        //HSSFRichTextString accessionString2 = new HSSFRichTextString("foodad");
        //accessionString.applyFont(2, 5, font);

        textbox.
        //textbox.setString(accessionString2);
        */

    }

    /**
     * Create the Excel File for output.
     * This function ALWAYS  creates XLSX files as this format is the only one
     * which will pass compatibility checks in data validation
     * <p/>
     * Dataset code is used as the basis for the outputfile name
     * If Dataset code is not available, it uses the metadata shortname for project
     * If that is not available it uses "output"
     *
     * @param defaultSheetname
     * @param uploadPath
     * @param fields
     *
     * @return
     */
    public File createExcelFile(String defaultSheetname, String uploadPath, List<String> fields) {

        // Create each of the sheets
        createInstructions(defaultSheetname);
        createDefaultSheet(defaultSheetname, fields);
        createDataFields(fields);
        createListsSheetAndValidations(fields);

        // Create the output Filename and Write Excel File
        String filename = null;
        if (this.datasetCode != null && !this.datasetCode.equals("")) {
            filename = this.datasetCode;
        } else if (getFims().getMetadata().getShortname() != null && !getFims().getMetadata().getShortname().equals("")) {
            filename = getFims().getMetadata().getShortname().replace(" ", "_");
        } else {
            filename = "output";
        }

        // Create the file: NOTE: this application ALWAYS should create XLSX files as this format is the only one
        // which will pass compatibility checks in data validation
        File file = PathManager.createUniqueFile(filename + ".xlsx", uploadPath);
        try {
            FileOutputStream out = new FileOutputStream(file);
            workbook.write(out);
            out.close();
        } catch (IOException e) {
            throw new FIMSRuntimeException(500, e);
        }

        return file;
    }

    /**
     * Create an Excel File for output using a pre-uploaded workbook containing a worksheet with data
     * This method assumes the appropriate constructor was called.
     *
     * @param defaultSheetname
     * @param uploadPath
     *
     * @return
     */
    public File createExcelFileFromExistingSources(String defaultSheetname, String uploadPath) {

        // Create each of the sheets
        createInstructions(defaultSheetname);

        // Set the defaultSheet to be the default sheet of the workbook
        defaultSheet = this.workbook.getSheet(defaultSheetname);

        // Getting list of field names...
        // I'm not sure if i should get ALL from mapping file or just the ones specified on the spreadsheet template
        // This method fetches all from mapping file
        XSSFRow row = workbook.getSheet(defaultSheetname).getRow(0);
        ArrayList<String> fields = new ArrayList<String>();
        Iterator it = row.iterator();
        while (it.hasNext()) {
            String fieldName = ((Cell) it.next()).toString();
            // TODO: test implications of adding or NOT adding BCID column at this point
            if (!fieldName.equalsIgnoreCase("BCID")) {
                fields.add(fieldName);
            }
        }

        createDataFields(fields);
        createListsSheetAndValidations(fields);

        // Create the output Filename and Write Excel File
        String filename = null;
        if (this.datasetCode != null && !this.datasetCode.equals("")) {
            filename = this.datasetCode;
        } else if (getFims().getMetadata().getShortname() != null && !getFims().getMetadata().getShortname().equals("")) {
            filename = getFims().getMetadata().getShortname().replace(" ", "_");
        } else {
            filename = "output";
        }

        // Create the file: NOTE: this application ALWAYS should create XLSX files as this format is the only one
        // which will pass compatibility checks in data validation
        File file = PathManager.createUniqueFile(filename + ".xlsx", uploadPath);
        try {
            FileOutputStream out = new FileOutputStream(file);
            workbook.write(out);
            out.close();
        } catch (IOException e) {
            throw new FIMSRuntimeException(500, e);
        }

        return file;
    }

    /**
     * Print the abstract text
     *
     * @return
     */
    public String printAbstract() {
        return getFims().getMetadata().getText_abstract();
    }

    /**
     * Format a key/value string to use in Instructions Sheet Header
     *
     * @param key
     * @param value
     *
     * @return
     */
    private XSSFRichTextString formatKeyValueString(String key, String value) {
        XSSFFont font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setBoldweight(Font.BOLDWEIGHT_BOLD);
        font.setFontHeightInPoints((short) 14);
        //String prefix = "Accession Number: ";
        XSSFRichTextString totalRichTextString = new XSSFRichTextString(key + value);
        Integer start = key.toString().length();
        Integer end = totalRichTextString.toString().length();
        // Only make the value portion of string RED
        //totalRichTextString.applyFont(start, end, font);
        // Just make the whole string RED
        totalRichTextString.applyFont(0, end, font);
        return totalRichTextString;
    }

    /**
     * main method is used for testing
     *
     * @param args
     */
    public static void main(String[] args) {
        // File configFile = new configurationFileFetcher(1, "tripleOutput", false).getOutputFile();
        //File file = new File("/Users/jdeck/IdeaProjects/biocode-fims/web_nmnh/docs/SIENT.xml");
        File file = new File("/Users/jdeck/Downloads/ucjeps_fims.xml");
        //templateProcessor t1 = new templateProcessor(file,"tripleOutput",false,12345,"DEMO4","ark:/99999/fk2");

        //System.out.println(t1.definition("hdimNumber"));


        /*
        templateProcessor t = new templateProcessor(file, "tripleOutput", false, 12345, "DEMO4", "ark:/21547/VR2");
        ArrayList<String> a = new ArrayList<String>();
               a.add("Locality");
               a.add("ScientificName");
               a.add("Coll_Num");

               File outputFile = t.createExcelFile("Samples", "tripleOutput", a);
               System.out.println(outputFile.getAbsoluteFile().toString());
        */


    }

}
