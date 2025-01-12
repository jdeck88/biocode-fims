package reader;

import java.sql.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import de.fuberlin.wiwiss.d2rq.sql.SQL;
import digester.Attribute;
import digester.Entity;
import digester.Fims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reader.plugins.TabularDataReader;
import settings.FIMSRuntimeException;
import settings.hasher;
import utils.sqlLiteNameCleaner;


/**
 * Takes a data source represented by a TabularDataReader and converts it to a
 * SQLite database.  Each table in the source data is converted to a matching
 * table in the SQLite database.
 */
public final class TabularDataConverter {
    TabularDataReader source;
    String dest;
    String tablename;

    private static Logger logger = LoggerFactory.getLogger(TabularDataConverter.class);
    /**
     * Constructs a new TabularDataConverter for the specified source.
     *
     * @param source A TabularDataReader with an open data source.
     */
    public TabularDataConverter(TabularDataReader source) {
        this(source, "");
    }

    /**
     * Constructs a new TabularDataConverter for the specified source and
     * destination database connection.
     *
     * @param source A TabularDataReader with an open data source.
     * @param dest   A valid SQLIte JDBC connection string.
     */
    public TabularDataConverter(TabularDataReader source, String dest) {
        // load the Sqlite JDBC driver
        //Class.forName("org.sqlite.JDBC");

        setSource(source);
        setDestination(dest);
    }

    /**
     * Set the source data for this TabularDataConverter.  The source
     * TabularDataReader must have a data source open and ready to access.
     *
     * @param source The data source from which to read.
     */
    public final void setSource(TabularDataReader source) {
        this.source = source;
        tablename = "";
    }

    /**
     * The SQLite JDBC connection string to use for the destination.
     *
     * @param dest A valid JDBC SQLite connection string.
     */
    public final void setDestination(String dest) {
        this.dest = dest;
    }

    /**
     * Get the JDBC connection string for the destination SQLite database.
     *
     * @return The JDBC connection string.
     */
    public String getDestination() {
        return dest;
    }

    /**
     * Specify a table name to use for storing the converted data in the
     * destination database.  This will only apply to the first table in a data
     * source, and is intended for data sources that don't explicitly provide a
     * meaningful table name, such as CSV files.
     *
     * @param tablename A valid SQLite table name.
     */
    public void setTableName(String tablename) {
        this.tablename = tablename;
    }

    /**
     * Gets the table name string to use for the first table in the data source.
     *
     * @return The table name string.
     */
    public String getTableName() {
        return tablename;
    }

    /**
     * Ensures that table and column names are valid SQLite identifiers that do
     * not require quoting in brackets for maximum compatibility.  Spaces and
     * periods are replaced with underscores, and if the name starts with a
     * digit, an underscore is added to the beginning of the name.  Any other
     * non-alphanumeric characters are removed.
     *
     * @param tname The table name to fix, if needed.
     * @return The corrected table name.
     */
    private String fixSQLiteIdentifierName(String tname) {
        sqlLiteNameCleaner cleaner = new sqlLiteNameCleaner();
        return cleaner.fixNames(tname);
    }

    /**
     * Reads the source data and converts it to tables in a Sqlite database.
     * Uses the database connection string provided in the constructor or in a
     * call to setDestination().  The name to use for the FIRST table in the
     * database can be specified by calling setTableName().  Otherwise, and for
     * all remaining tables, the table names are taken from the source data
     * reader.  Any tables that already exist in the destination database will
     * be DROPPED.  Destination tables will have columns matching the names and
     * number of elements in the first row of each table in the source data.
     * All rows from the source are copied to the new table.
     * If the input data source is a Darwin Core archive, convert() will also
     * attempt to "re-normalize" the archive data.  This task is handed off to
     * an instance of DwCAFixer.
     */
    public void convert(digester.Mapping mapping) {
        //int tablecnt = 0;
        Connection connection = null;
        try {
            String tname = source.getCurrentTableName();
            connection = DriverManager.getConnection(dest);

            if (source.tableHasNextRow()) {
                String fixedtname = fixSQLiteIdentifierName(tname);
                buildTable(connection, fixedtname);
                buildHashes(connection, mapping, fixedtname);
            }

            // TODO: loop tables as the original triplifier did (see commented code below).  For now, we just name one table
            /*
             while (source.hasNextTable()) {
              source.moveToNextTable();
              tablecnt++;
              // If the user supplied a name for the first table in the data
              // source, use it.  Otherwise, take the table name from the data
              // source.
              if ((tablecnt == 1) && !tablename.equals(""))
                  tname = tablename;
              else
                  tname = source.getCurrentTableName();

              if (source.tableHasNextRow())
                  buildTable(conn, fixSQLiteIdentifierName(tname));
          }  */
        } catch (SQLException e) {
            // If we through an excaption here, it could be ANYTHING that SQLlite doesn't like, including duplicate columnnames
            throw new FIMSRuntimeException(500, e);
        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (SQLException e) {
                logger.warn("SQLException", e);
            }
        }
    }

    /**
     * build Hash Keys in SQLLite table when we encounter a worksheetUniqueKey that ends in HASH
     *
     * @param connection
     * @param mapping
     */
    private void buildHashes(Connection connection, digester.Mapping mapping, String tname) {
        // Loop through entities and find which ones define HASH
        LinkedList<Entity> entities = mapping.getEntities();
        Iterator it = entities.iterator();
        Statement stmt = null;
        try {
            stmt = connection.createStatement();

            while (it.hasNext()) {
                Entity entity = (Entity) it.next();
                if (entity.getWorksheetUniqueKey().contains("HASH")) {

                    // Add this column identifier
                    String alter = "ALTER TABLE " + tname + " ADD COLUMN " + entity.getWorksheetUniqueKey() + " text";
                    stmt.executeUpdate(alter);

                    LinkedList<Attribute> attributes = entity.getAttributes();
                    Iterator attributesIt = attributes.iterator();
                    StringBuilder sb = new StringBuilder();
                    sb.append("SELECT rowid,");
                    while (attributesIt.hasNext()) {
                        Attribute attribute = (Attribute) attributesIt.next();
                        sb.append(attribute.getColumn());
                        if (attributesIt.hasNext())
                            sb.append(" || ");
                    }
                    sb.append(" AS toHash FROM " + tname);
                    //System.out.println(sb.toString());
                    ResultSet rs = stmt.executeQuery(sb.toString());

                    Statement updateStatement = connection.createStatement();
                    hasher hasher = new hasher();
                    updateStatement.execute("BEGIN TRANSACTION");
                    while (rs.next()) {
                        String update = "UPDATE " + tname +
                                " SET " + entity.getWorksheetUniqueKey() + " = \"" +
                                hasher.hasherDigester(rs.getString("toHash")) + "\" " +
                                " WHERE rowid = " + rs.getString("rowid");
                        updateStatement.executeUpdate(update);
                    }
                    updateStatement.execute("COMMIT");
                    updateStatement.close();
                }
            }
        } catch (SQLException e) {
            throw new FIMSRuntimeException(500, e);
        } finally {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.warn("SQLException", e);
            }
        }
    }

    /**
     * Creates a single table in the destination database using the current
     * table in the data source.  If the specified table name already exists in
     * the database, IT IS DROPPED.  A new table with columns matching the names
     * and number of elements in the first row of the source data is created,
     * and all rows from the source are copied to the new table.  If a data
     * source returns a blank column name, then a machine-generated column name
     * will be used.
     *
     * @param conn  A valid connection to a destination database.
     * @param tname The name to use for the table in the destination database.
     */
    private void buildTable(Connection conn, String tname) {
        int colcnt, cnt;
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            // Counter for machine-generated column names.
            int col_cnt = 0;

            // Generate a short string of random characters to use for machine-
            // generated column names if the data source provides a blank column
            // name.
            char[] rand_prefix_arr = new char[10];
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            int alphindex;
            Random randgen = new Random();
            for (cnt = 0; cnt < rand_prefix_arr.length; cnt++) {
                alphindex = randgen.nextInt(alphabet.length());
                rand_prefix_arr[cnt] = alphabet.charAt(alphindex);
            }
            String rand_prefix = String.copyValueOf(rand_prefix_arr);

            // if this table exists, drop it
            stmt.executeUpdate("DROP TABLE IF EXISTS [" + tname + "]");

            // set up the table definition query
            String query = "CREATE TABLE [" + tname + "] (";
            colcnt = 0;
            for (String colname : source.tableGetNextRow()) {
                if (colcnt++ > 0)
                    query += ", ";
                // If the column name is blank, generate a suitable name.
                if (colname.trim().equals("")) {
                    colname = tname + "_" + rand_prefix + "_" + col_cnt;
                    col_cnt++;
                }
                colname = fixSQLiteIdentifierName(colname);
                query += "\"" + colname + "\"";
            }
            query += ")";
            //fimsPrinter.out.println(query);

            // create the table
            stmt.executeUpdate(query);

            // create a prepared statement for insert queries
            query = "INSERT INTO [" + tname + "] VALUES (";
            for (cnt = 0; cnt < colcnt; cnt++) {
                if (cnt > 0)
                    query += ", ";
                query += "?";
            }
            query += ")";
            //fimsPrinter.out.println(query);
            PreparedStatement insstmt = conn.prepareStatement(query);

            // Start a new transaction for all of the INSERT statements.  This
            // dramatically improves the run time from many minutes for a large data
            // source to a matter of seconds.
            stmt.execute("BEGIN TRANSACTION");

            // populate the table with the source data
            while (source.tableHasNextRow()) {
                cnt = 0;
                StringBuilder sb = new StringBuilder();
                for (String dataval : source.tableGetNextRow()) {
                    // Ignore any data that is NA or na by setting it to blank
                    if (dataval.equalsIgnoreCase("na"))
                        dataval="";
                    sb.append(dataval);
                    insstmt.setString(++cnt, dataval);
                }

                // Supply blank strings for any missing columns.  This does not appear
                // to be strictly necessary, at least with the Sqlite driver we're
                // using, but it is included as insurance against future changes.
                while (cnt < colcnt) {
                    insstmt.setString(++cnt, "");
                }

                // Only execute this portion of the strinbuilder identified ANY datavalues for this row
                if (!sb.toString().equals("")) {
                    // add the row to the database
                    insstmt.executeUpdate();
                }
            }

            try {
                insstmt.close();
            } catch (SQLException e) {
                logger.warn("SQLException", e);
            }

            // end the transaction
            stmt.execute("COMMIT");
        } catch (SQLException e) {
            throw new FIMSRuntimeException(500, e);
        } finally {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.warn("SQLException", e);
            }
        }
    }
}
