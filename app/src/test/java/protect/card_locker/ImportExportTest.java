package protect.card_locker;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import com.google.zxing.BarcodeFormat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class ImportExportTest
{
    private Activity activity;
    private DBHelper db;
    private long nowMs;
    private long lastYearMs;
    private final int MONTHS_PER_YEAR = 12;

    private final String BARCODE_DATA = "428311627547";
    private final String BARCODE_TYPE = BarcodeFormat.UPC_A.name();

    @Before
    public void setUp()
    {
        activity = Robolectric.setupActivity(MainActivity.class);
        db = new DBHelper(activity);
        nowMs = System.currentTimeMillis();

        Calendar lastYear = Calendar.getInstance();
        lastYear.set(Calendar.YEAR, lastYear.get(Calendar.YEAR)-1);
        lastYearMs = lastYear.getTimeInMillis();
    }

    /**
     * Add the given number of cards, each with
     * an index in the store name.
     * @param cardsToAdd
     */
    private void addLoyaltyCards(int cardsToAdd)
    {
        // Add in reverse order to test sorting
        for(int index = cardsToAdd; index > 0; index--)
        {
            String storeName = String.format("store, \"%4d", index);
            String note = String.format("note, \"%4d", index);
            long id = db.insertLoyaltyCard(storeName, note, null, BARCODE_DATA, BARCODE_TYPE, index, 0);
            boolean result = (id != -1);
            assertTrue(result);
        }

        assertEquals(cardsToAdd, db.getLoyaltyCardCount());
    }

    private void addLoyaltyCardsFiveStarred()
    {
        int cardsToAdd = 9;
        // Add in reverse order to test sorting
        for(int index = cardsToAdd; index > 4; index--)
        {
            String storeName = String.format("store, \"%4d", index);
            String note = String.format("note, \"%4d", index);
            long id = db.insertLoyaltyCard(storeName, note, null, BARCODE_DATA, BARCODE_TYPE, index, 1);
            boolean result = (id != -1);
            assertTrue(result);
        }
        for(int index = cardsToAdd-5; index > 0; index--)
        {
            String storeName = String.format("store, \"%4d", index);
            String note = String.format("note, \"%4d", index);
            //if index is even
            long id = db.insertLoyaltyCard(storeName, note, null, BARCODE_DATA, BARCODE_TYPE, index, 0);
            boolean result = (id != -1);
            assertTrue(result);
        }
        assertEquals(cardsToAdd, db.getLoyaltyCardCount());
    }

    private void addLoyaltyCardsWithExpiryNeverPastTodayFuture()
    {
        long id = db.insertLoyaltyCard("No Expiry", "", null, BARCODE_DATA, BARCODE_TYPE, 0, 0);
        boolean result = (id != -1);
        assertTrue(result);

        id = db.insertLoyaltyCard("Past", "", new Date((long) 1), BARCODE_DATA, BARCODE_TYPE, 0, 0);
        result = (id != -1);
        assertTrue(result);

        id = db.insertLoyaltyCard("Today", "", new Date(), BARCODE_DATA, BARCODE_TYPE, 0, 0);
        result = (id != -1);
        assertTrue(result);

        // This will break after 19 January 2038
        // If someone is still maintaining this code base by then: I love you
        id = db.insertLoyaltyCard("Future", "", new Date(2147483648L), BARCODE_DATA, BARCODE_TYPE, 0, 0);
        result = (id != -1);
        assertTrue(result);

        assertEquals(4, db.getLoyaltyCardCount());
    }

    private void addGroups(int groupsToAdd)
    {
        // Add in reverse order to test sorting
        for(int index = groupsToAdd; index > 0; index--)
        {
            String groupName = String.format("group, \"%4d", index);
            long id = db.insertGroup(groupName);
            boolean result = (id != -1);
            assertTrue(result);
        }

        assertEquals(groupsToAdd, db.getGroupCount());
    }

    /**
     * Check that all of the cards follow the pattern
     * specified in addLoyaltyCards(), and are in sequential order
     * where the smallest card's index is 1
     */
    private void checkLoyaltyCards()
    {
        Cursor cursor = db.getLoyaltyCardCursor();
        int index = 1;

        while(cursor.moveToNext())
        {
            LoyaltyCard card = LoyaltyCard.toLoyaltyCard(cursor);

            String expectedStore = String.format("store, \"%4d", index);
            String expectedNote = String.format("note, \"%4d", index);

            assertEquals(expectedStore, card.store);
            assertEquals(expectedNote, card.note);
            assertEquals(BARCODE_DATA, card.cardId);
            assertEquals(BARCODE_TYPE, card.barcodeType);
            assertEquals(Integer.valueOf(index), card.headerColor);
            assertEquals(0, card.starStatus);

            index++;
        }
        cursor.close();
    }

    /**
     * Check that all of the cards follow the pattern
     * specified in addLoyaltyCardsSomeStarred(), and are in sequential order
     * with starred ones first
     */
    private void checkLoyaltyCardsFiveStarred()
        {
            Cursor cursor = db.getLoyaltyCardCursor();
            int index = 5;

            while(index<10)
        {
            cursor.moveToNext();
            LoyaltyCard card = LoyaltyCard.toLoyaltyCard(cursor);

            String expectedStore = String.format("store, \"%4d", index);
            String expectedNote = String.format("note, \"%4d", index);

            assertEquals(expectedStore, card.store);
            assertEquals(expectedNote, card.note);
            assertEquals(BARCODE_DATA, card.cardId);
            assertEquals(BARCODE_TYPE, card.barcodeType);
            assertEquals(Integer.valueOf(index), card.headerColor);
            assertEquals(1, card.starStatus);

            index++;
        }

        index = 1;
        while(cursor.moveToNext() && index<5)
    {
        LoyaltyCard card = LoyaltyCard.toLoyaltyCard(cursor);

        String expectedStore = String.format("store, \"%4d", index);
        String expectedNote = String.format("note, \"%4d", index);

        assertEquals(expectedStore, card.store);
        assertEquals(expectedNote, card.note);
        assertEquals(BARCODE_DATA, card.cardId);
        assertEquals(BARCODE_TYPE, card.barcodeType);
        assertEquals(Integer.valueOf(index), card.headerColor);
        assertEquals(0, card.starStatus);

        index++;
    }

        cursor.close();
    }

    /**
     * Check that all of the groups follow the pattern
     * specified in addGroups(), and are in sequential order
     * where the smallest group's index is 1
     */
    private void checkGroups()
    {
        Cursor cursor = db.getGroupCursor();
        int index = db.getGroupCount();

        while(cursor.moveToNext())
        {
            Group group = Group.toGroup(cursor);

            String expectedGroupName = String.format("group, \"%4d", index);

            assertEquals(expectedGroupName, group._id);

            index--;
        }
        cursor.close();
    }

    /**
     * Delete the contents of the database
     */
    private void clearDatabase()
    {
        SQLiteDatabase database = db.getWritableDatabase();
        database.execSQL("delete from " + DBHelper.LoyaltyCardDbIds.TABLE);
        database.execSQL("delete from " + DBHelper.LoyaltyCardDbGroups.TABLE);
        database.execSQL("delete from " + DBHelper.LoyaltyCardDbIdsGroups.TABLE);
        database.close();

        assertEquals(0, db.getLoyaltyCardCount());
    }

    @Test
    public void multipleCardsExportImport() throws IOException
    {
        final int NUM_CARDS = 10;

        for(DataFormat format : DataFormat.values())
        {
            addLoyaltyCards(NUM_CARDS);

            ByteArrayOutputStream outData = new ByteArrayOutputStream();
            OutputStreamWriter outStream = new OutputStreamWriter(outData);

            // Export data to CSV format
            boolean result = MultiFormatExporter.exportData(db, outStream, format);
            assertTrue(result);
            outStream.close();

            clearDatabase();

            ByteArrayInputStream inData = new ByteArrayInputStream(outData.toByteArray());
            InputStreamReader inStream = new InputStreamReader(inData);

            // Import the CSV data
            result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
            assertTrue(result);

            assertEquals(NUM_CARDS, db.getLoyaltyCardCount());

            checkLoyaltyCards();

            // Clear the database for the next format under test
            clearDatabase();
        }
    }

    @Test
    public void multipleCardsExportImportSomeStarred() throws IOException
    {
        final int NUM_CARDS = 9;

        for(DataFormat format : DataFormat.values())
        {
            addLoyaltyCardsFiveStarred();

            ByteArrayOutputStream outData = new ByteArrayOutputStream();
            OutputStreamWriter outStream = new OutputStreamWriter(outData);

            // Export data to CSV format
            boolean result = MultiFormatExporter.exportData(db, outStream, format);
            assertTrue(result);
            outStream.close();

            clearDatabase();

            ByteArrayInputStream inData = new ByteArrayInputStream(outData.toByteArray());
            InputStreamReader inStream = new InputStreamReader(inData);

            // Import the CSV data
            result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
            assertTrue(result);

            assertEquals(NUM_CARDS, db.getLoyaltyCardCount());

            checkLoyaltyCardsFiveStarred();

            // Clear the database for the next format under test
            clearDatabase();
        }
    }

    private List<String> groupsToGroupNames(List<Group> groups)
    {
        List<String> groupNames = new ArrayList<>();

        for (Group group : groups) {
            groupNames.add(group._id);
        }

        return groupNames;
    }

    @Test
    public void multipleCardsExportImportWithGroups() throws IOException
    {
        final int NUM_CARDS = 10;
        final int NUM_GROUPS = 3;

        for(DataFormat format : DataFormat.values())
        {
            addLoyaltyCards(NUM_CARDS);
            addGroups(NUM_GROUPS);

            List<Group> emptyGroup = new ArrayList<>();

            List<Group> groupsForOne = new ArrayList<>();
            groupsForOne.add(db.getGroup("group, \"   1"));

            List<Group> groupsForTwo = new ArrayList<>();
            groupsForTwo.add(db.getGroup("group, \"   1"));
            groupsForTwo.add(db.getGroup("group, \"   2"));

            List<Group> groupsForThree = new ArrayList<>();
            groupsForThree.add(db.getGroup("group, \"   1"));
            groupsForThree.add(db.getGroup("group, \"   2"));
            groupsForThree.add(db.getGroup("group, \"   3"));

            List<Group> groupsForFour = new ArrayList<>();
            groupsForFour.add(db.getGroup("group, \"   1"));
            groupsForFour.add(db.getGroup("group, \"   2"));
            groupsForFour.add(db.getGroup("group, \"   3"));

            List<Group> groupsForFive = new ArrayList<>();
            groupsForFive.add(db.getGroup("group, \"   1"));
            groupsForFive.add(db.getGroup("group, \"   3"));

            db.setLoyaltyCardGroups(1, groupsForOne);
            db.setLoyaltyCardGroups(2, groupsForTwo);
            db.setLoyaltyCardGroups(3, groupsForThree);
            db.setLoyaltyCardGroups(4, groupsForFour);
            db.setLoyaltyCardGroups(5, groupsForFive);

            ByteArrayOutputStream outData = new ByteArrayOutputStream();
            OutputStreamWriter outStream = new OutputStreamWriter(outData);

            // Export data to CSV format
            boolean result = MultiFormatExporter.exportData(db, outStream, format);
            assertTrue(result);
            outStream.close();

            clearDatabase();

            ByteArrayInputStream inData = new ByteArrayInputStream(outData.toByteArray());
            InputStreamReader inStream = new InputStreamReader(inData);

            // Import the CSV data
            result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
            assertTrue(result);

            assertEquals(NUM_CARDS, db.getLoyaltyCardCount());
            assertEquals(NUM_GROUPS, db.getGroupCount());

            checkLoyaltyCards();
            checkGroups();

            assertEquals(groupsToGroupNames(groupsForOne), groupsToGroupNames(db.getLoyaltyCardGroups(1)));
            assertEquals(groupsToGroupNames(groupsForTwo), groupsToGroupNames(db.getLoyaltyCardGroups(2)));
            assertEquals(groupsToGroupNames(groupsForThree), groupsToGroupNames(db.getLoyaltyCardGroups(3)));
            assertEquals(groupsToGroupNames(groupsForFour), groupsToGroupNames(db.getLoyaltyCardGroups(4)));
            assertEquals(groupsToGroupNames(groupsForFive), groupsToGroupNames(db.getLoyaltyCardGroups(5)));
            assertEquals(emptyGroup, db.getLoyaltyCardGroups(6));
            assertEquals(emptyGroup, db.getLoyaltyCardGroups(7));
            assertEquals(emptyGroup, db.getLoyaltyCardGroups(8));
            assertEquals(emptyGroup, db.getLoyaltyCardGroups(9));
            assertEquals(emptyGroup, db.getLoyaltyCardGroups(10));

            // Clear the database for the next format under test
            clearDatabase();
        }
    }

    @Test
    public void importExistingCardsNotReplace() throws IOException
    {
        final int NUM_CARDS = 10;

        for(DataFormat format : DataFormat.values())
        {
            addLoyaltyCards(NUM_CARDS);

            ByteArrayOutputStream outData = new ByteArrayOutputStream();
            OutputStreamWriter outStream = new OutputStreamWriter(outData);

            // Export into CSV data
            boolean result = MultiFormatExporter.exportData(db, outStream, format);
            assertTrue(result);
            outStream.close();

            ByteArrayInputStream inData = new ByteArrayInputStream(outData.toByteArray());
            InputStreamReader inStream = new InputStreamReader(inData);

            // Import the CSV data on top of the existing database
            result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
            assertTrue(result);

            assertEquals(NUM_CARDS, db.getLoyaltyCardCount());

            checkLoyaltyCards();

            // Clear the database for the next format under test
            clearDatabase();
        }
    }

    @Test
    public void corruptedImportNothingSaved() throws IOException
    {
        final int NUM_CARDS = 10;

        for(DataFormat format : DataFormat.values())
        {
            addLoyaltyCards(NUM_CARDS);

            ByteArrayOutputStream outData = new ByteArrayOutputStream();
            OutputStreamWriter outStream = new OutputStreamWriter(outData);

            // Export data to CSV format
            boolean result = MultiFormatExporter.exportData(db, outStream, format);
            assertTrue(result);

            clearDatabase();

            // commons-csv would throw a RuntimeException if an entry was quotes but had
            // content after. For example:
            //   abc,def,""abc,abc
            //             ^ after the quote there should only be a , \n or EOF
            String corruptEntry = "ThisStringIsLikelyNotPartOfAnyFormat,\"\"a";

            ByteArrayInputStream inData = new ByteArrayInputStream((outData.toString() + corruptEntry).getBytes());
            InputStreamReader inStream = new InputStreamReader(inData);

            // Attempt to import the CSV data
            result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
            assertEquals(false, result);

            assertEquals(0, db.getLoyaltyCardCount());

            clearDatabase();
        }
    }

    class TestTaskCompleteListener implements ImportExportTask.TaskCompleteListener
    {
        Boolean success;

        public void onTaskComplete(boolean success)
        {
            this.success = success;
        }
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void useImportExportTask() throws FileNotFoundException
    {
        final int NUM_CARDS = 10;

        final File sdcardDir = Environment.getExternalStorageDirectory();
        final File exportFile = new File(sdcardDir, "Catima.csv");

        for(DataFormat format : DataFormat.values())
        {
            addLoyaltyCards(NUM_CARDS);

            TestTaskCompleteListener listener = new TestTaskCompleteListener();

            // Export to the file
            FileOutputStream fileOutputStream = new FileOutputStream(exportFile);
            ImportExportTask task = new ImportExportTask(activity, format, fileOutputStream, listener);
            task.execute();

            // Actually run the task to completion
            Robolectric.flushBackgroundThreadScheduler();

            // Check that the listener was executed
            assertNotNull(listener.success);
            assertEquals(true, listener.success);

            clearDatabase();

            // Import everything back from the default location

            listener = new TestTaskCompleteListener();

            FileInputStream fileStream = new FileInputStream(exportFile);

            task = new ImportExportTask(activity, format, fileStream, listener);
            task.execute();

            // Actually run the task to completion
            Robolectric.flushBackgroundThreadScheduler();

            // Check that the listener was executed
            assertNotNull(listener.success);
            assertEquals(true, listener.success);

            assertEquals(NUM_CARDS, db.getLoyaltyCardCount());

            checkLoyaltyCards();

            // Clear the database for the next format under test
            clearDatabase();
        }
    }

    @Test
    public void importWithoutColorsV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,0";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertTrue(result);
        assertEquals(1, db.getLoyaltyCardCount());

        LoyaltyCard card = db.getLoyaltyCard(1);

        assertEquals("store", card.store);
        assertEquals("note", card.note);
        assertEquals(null, card.expiry);
        assertEquals("12345", card.cardId);
        assertEquals("type", card.barcodeType);
        assertEquals(0, card.starStatus);
        assertNull(card.headerColor);

        clearDatabase();
    }

    @Test
    public void importWithoutNullColorsV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,,,0";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertTrue(result);
        assertEquals(1, db.getLoyaltyCardCount());

        LoyaltyCard card = db.getLoyaltyCard(1);

        assertEquals("store", card.store);
        assertEquals("note", card.note);
        assertEquals(null, card.expiry);
        assertEquals("12345", card.cardId);
        assertEquals("type", card.barcodeType);
        assertEquals(0, card.starStatus);
        assertNull(card.headerColor);

        clearDatabase();
    }

    @Test
    public void importWithoutInvalidColorsV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,not a number,invalid,0";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertEquals(false, result);
        assertEquals(0, db.getLoyaltyCardCount());

        clearDatabase();
    }

    @Test
    public void importWithNoBarcodeTypeV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,,1,1,0";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertEquals(true, result);
        assertEquals(1, db.getLoyaltyCardCount());

        LoyaltyCard card = db.getLoyaltyCard(1);

        assertEquals("store", card.store);
        assertEquals("note", card.note);
        assertEquals(null, card.expiry);
        assertEquals("12345", card.cardId);
        assertEquals("", card.barcodeType);
        assertEquals(0, card.starStatus);
        assertEquals(1, (long) card.headerColor);

        clearDatabase();
    }

    @Test
    public void importWithStarredFieldV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,1,1,1";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertEquals(true, result);
        assertEquals(1, db.getLoyaltyCardCount());

        LoyaltyCard card = db.getLoyaltyCard(1);

        assertEquals("store", card.store);
        assertEquals("note", card.note);
        assertEquals(null, card.expiry);
        assertEquals("12345", card.cardId);
        assertEquals("type", card.barcodeType);
        assertEquals(1, card.starStatus);
        assertEquals(1, (long) card.headerColor);

        clearDatabase();
    }

    @Test
    public void importWithNoStarredFieldV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,1,1,";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertEquals(true, result);
        assertEquals(1, db.getLoyaltyCardCount());

        LoyaltyCard card = db.getLoyaltyCard(1);

        assertEquals("store", card.store);
        assertEquals("note", card.note);
        assertEquals(null, card.expiry);
        assertEquals("12345", card.cardId);
        assertEquals("type", card.barcodeType);
        assertEquals(0, card.starStatus);
        assertEquals(1, (long) card.headerColor);

        clearDatabase();
    }

    @Test
    public void importWithInvalidStarFieldV1() throws IOException
    {
        String csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,1,1,2";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        InputStreamReader inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        boolean result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertTrue(result);
        assertEquals(1, db.getLoyaltyCardCount());

        csvText = "";
        csvText += DBHelper.LoyaltyCardDbIds.ID + "," +
                DBHelper.LoyaltyCardDbIds.STORE + "," +
                DBHelper.LoyaltyCardDbIds.NOTE + "," +
                DBHelper.LoyaltyCardDbIds.CARD_ID + "," +
                DBHelper.LoyaltyCardDbIds.BARCODE_TYPE + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.HEADER_TEXT_COLOR + "," +
                DBHelper.LoyaltyCardDbIds.STAR_STATUS + "\n";

        csvText += "1,store,note,12345,type,1,1,text";

        inputStream = new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8));
        inStream = new InputStreamReader(inputStream);

        // Import the CSV data
        result = MultiFormatImporter.importData(db, inStream, DataFormat.CSV);
        assertTrue(result);
        assertEquals(1, db.getLoyaltyCardCount());

        LoyaltyCard card = db.getLoyaltyCard(1);

        assertEquals("store", card.store);
        assertEquals("note", card.note);
        assertEquals(null, card.expiry);
        assertEquals("12345", card.cardId);
        assertEquals("type", card.barcodeType);
        assertEquals(0, card.starStatus);
        assertEquals(1, (long) card.headerColor);

        clearDatabase();
    }


    private void checkLoyaltyCardsExpiry()
    {
        Cursor cursor = db.getLoyaltyCardCursor();
        cursor.moveToNext();
        LoyaltyCard card = LoyaltyCard.toLoyaltyCard(cursor);
        assertEquals("Never", card.store);
        assertEquals("", card.note);
        assertEquals(null, card.expiry);
        assertEquals(BARCODE_DATA, card.cardId);
        assertEquals(BARCODE_TYPE, card.barcodeType);
        assertEquals(Integer.valueOf(0), card.headerColor);
        assertEquals(0, card.starStatus);

        cursor.moveToNext();
        card = LoyaltyCard.toLoyaltyCard(cursor);
        assertEquals("Past", card.store);
        assertEquals("", card.note);
        assertTrue(card.expiry.before(new Date()));
        assertEquals(BARCODE_DATA, card.cardId);
        assertEquals(BARCODE_TYPE, card.barcodeType);
        assertEquals(Integer.valueOf(0), card.headerColor);
        assertEquals(0, card.starStatus);

        cursor.moveToNext();
        card = LoyaltyCard.toLoyaltyCard(cursor);
        assertEquals("Today", card.store);
        assertEquals("", card.note);
        assertTrue(card.expiry.before(new Date(new Date().getTime()+86400)));
        assertTrue(card.expiry.after(new Date(new Date().getTime()-86400)));
        assertEquals(BARCODE_DATA, card.cardId);
        assertEquals(BARCODE_TYPE, card.barcodeType);
        assertEquals(Integer.valueOf(0), card.headerColor);
        assertEquals(0, card.starStatus);

        cursor.moveToNext();
        card = LoyaltyCard.toLoyaltyCard(cursor);
        assertEquals("Future", card.store);
        assertEquals("", card.note);
        assertTrue(card.expiry.after(new Date(new Date().getTime()+86400)));
        assertEquals(BARCODE_DATA, card.cardId);
        assertEquals(BARCODE_TYPE, card.barcodeType);
        assertEquals(Integer.valueOf(0), card.headerColor);
        assertEquals(0, card.starStatus);

        cursor.close();
    }
}
