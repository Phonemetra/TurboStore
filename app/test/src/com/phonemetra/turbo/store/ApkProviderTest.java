package com.phonemetra.turbo.store;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.phonemetra.turbo.store.data.Apk;
import com.phonemetra.turbo.store.data.ApkProvider;
import com.phonemetra.turbo.store.data.RepoProvider;
import com.phonemetra.turbo.store.mock.MockApk;
import com.phonemetra.turbo.store.mock.MockApp;
import com.phonemetra.turbo.store.mock.MockRepo;

import java.util.ArrayList;
import java.util.List;

public class ApkProviderTest extends BaseApkProviderTest {

    /**
     * I want to test the protected {@link com.phonemetra.turbo.store.data.ApkProvider#getContentUri(java.util.List)}
     * method, but don't want to make it public. This exposes it.
     */
    private static class PublicApkProvider extends ApkProvider {

        public static final int MAX_APKS_TO_QUERY = ApkProvider.MAX_APKS_TO_QUERY;

        public static Uri getContentUri(List<Apk> apks) {
            return ApkProvider.getContentUri(apks);
        }
    }

    public void testUris() {
        assertInvalidUri(ApkProvider.getAuthority());
        assertInvalidUri(RepoProvider.getContentUri());

        List<Apk> apks = new ArrayList<Apk>(3);
        for (int i = 0; i < 10; i ++) {
            apks.add(new MockApk("com.example." + i, i));
        }

        assertValidUri(ApkProvider.getContentUri());
        assertValidUri(ApkProvider.getAppUri("com.phonemetra.turbo.store"));
        assertValidUri(ApkProvider.getContentUri(new MockApk("com.phonemetra.turbo.store", 100)));
        assertValidUri(ApkProvider.getContentUri());
        assertValidUri(PublicApkProvider.getContentUri(apks));
        assertValidUri(ApkProvider.getContentUri("com.phonemetra.turbo.store", 100));
        assertValidUri(ApkProvider.getRepoUri(1000));

        List<Apk> manyApks = new ArrayList<Apk>(PublicApkProvider.MAX_APKS_TO_QUERY - 5);
        for (int i = 0; i < PublicApkProvider.MAX_APKS_TO_QUERY - 1; i ++) {
            manyApks.add(new MockApk("com.example." + i, i));
        }
        assertValidUri(PublicApkProvider.getContentUri(manyApks));

        manyApks.add(new MockApk("com.phonemetra.turbo.store.1", 1));
        manyApks.add(new MockApk("com.phonemetra.turbo.store.2", 2));
        try {
            // Technically, it is a valid URI, because it doesn't
            // throw an UnsupportedOperationException. However it
            // is still not okay (we run out of bindable parameters
            // in the sqlite query.
            assertValidUri(PublicApkProvider.getContentUri(manyApks));
            fail();
        } catch (IllegalArgumentException e) {
            // This is the expected error behaviour.
        } catch (Exception e) {
            fail();
        }
    }

    public void testAppApks() {
        for (int i = 1; i <= 10; i ++) {
            TestUtils.insertApk(this, "com.phonemetra.turbo.store", i);
            TestUtils.insertApk(this, "com.example", i);
        }

        assertTotalApkCount(20);

        Cursor fdroidApks = getMockContentResolver().query(
                ApkProvider.getAppUri("com.phonemetra.turbo.store"),
                getMinimalProjection(),
                null, null, null);
        assertResultCount(10, fdroidApks);
        assertBelongsToApp(fdroidApks, "com.phonemetra.turbo.store");

        Cursor exampleApks = getMockContentResolver().query(
                ApkProvider.getAppUri("com.example"),
                getMinimalProjection(),
                null, null, null);
        assertResultCount(10, exampleApks);
        assertBelongsToApp(exampleApks, "com.example");

        ApkProvider.Helper.deleteApksByApp(getMockContext(), new MockApp("com.example"));

        Cursor all = queryAllApks();
        assertResultCount(10, all);
        assertBelongsToApp(all, "com.phonemetra.turbo.store");
    }

    public void testInvalidUpdateUris() {
        Apk apk = new MockApk("com.phonemetra.turbo.store", 10);

        List<Apk> apks = new ArrayList<Apk>();
        apks.add(apk);

        assertCantUpdate(ApkProvider.getContentUri());
        assertCantUpdate(ApkProvider.getAppUri("com.phonemetra.turbo.store"));
        assertCantUpdate(ApkProvider.getRepoUri(1));
        assertCantUpdate(PublicApkProvider.getContentUri(apks));
        assertCantUpdate(Uri.withAppendedPath(ApkProvider.getContentUri(), "some-random-path"));

        // The only valid ones are:
        //  ApkProvider.getContentUri(apk)
        //  ApkProvider.getContentUri(id, version)
        // which are tested elsewhere.
    }

    public void testDeleteArbitraryApks() {
        Apk one   = insertApkForRepo("com.example.one", 1, 10);
        Apk two   = insertApkForRepo("com.example.two", 1, 10);
        Apk three = insertApkForRepo("com.example.three", 1, 10);
        Apk four  = insertApkForRepo("com.example.four", 1, 10);
        Apk five  = insertApkForRepo("com.example.five", 1, 10);

        assertTotalApkCount(5);

        assertEquals("com.example.one", one.id);
        assertEquals("com.example.two", two.id);
        assertEquals("com.example.five", five.id);

        String[] expectedIds = {
            "com.example.one",
            "com.example.two",
            "com.example.three",
            "com.example.four",
            "com.example.five",
        };

        List<Apk> all = ApkProvider.Helper.findByRepo(getSwappableContext(), new MockRepo(10), ApkProvider.DataColumns.ALL);
        List<String> actualIds = new ArrayList<String>();
        for (Apk apk : all) {
            actualIds.add(apk.id);
        }

        TestUtils.assertContainsOnly(actualIds, expectedIds);

        List<Apk> toDelete = new ArrayList<Apk>(3);
        toDelete.add(two);
        toDelete.add(three);
        toDelete.add(four);
        ApkProvider.Helper.deleteApks(getSwappableContext(), toDelete);

        assertTotalApkCount(2);

        List<Apk> allRemaining = ApkProvider.Helper.findByRepo(getSwappableContext(), new MockRepo(10), ApkProvider.DataColumns.ALL);
        List<String> actualRemainingIds = new ArrayList<String>();
        for (Apk apk : allRemaining) {
            actualRemainingIds.add(apk.id);
        }

        String[] expectedRemainingIds = {
            "com.example.one",
            "com.example.five",
        };

        TestUtils.assertContainsOnly(actualRemainingIds, expectedRemainingIds);
    }

    public void testInvalidDeleteUris() {
        Apk apk = new MockApk("com.phonemetra.turbo.store", 10);

        assertCantDelete(ApkProvider.getContentUri());
        assertCantDelete(ApkProvider.getContentUri("com.phonemetra.turbo.store", 10));
        assertCantDelete(ApkProvider.getContentUri(apk));
        assertCantDelete(Uri.withAppendedPath(ApkProvider.getContentUri(), "some-random-path"));
    }

    public void testRepoApks() {

        final long REPO_KEEP = 1;
        final long REPO_DELETE = 2;

        // Insert apks into two repos, one of which we will later purge the
        // the apks from.
        for (int i = 1; i <= 5; i ++) {
            insertApkForRepo("com.phonemetra.turbo.store", i, REPO_KEEP);
            insertApkForRepo("com.example." + i, 1, REPO_DELETE);
        }
        for (int i = 6; i <= 10; i ++) {
            insertApkForRepo("com.phonemetra.turbo.store", i, REPO_DELETE);
            insertApkForRepo("com.example." + i, 1, REPO_KEEP);
        }

        assertTotalApkCount(20);

        Cursor cursor = getMockContentResolver().query(
            ApkProvider.getRepoUri(REPO_DELETE), getMinimalProjection(), null, null, null);
        assertResultCount(10, cursor);
        assertBelongsToRepo(cursor, REPO_DELETE);

        int count = ApkProvider.Helper.deleteApksByRepo(getMockContext(), new MockRepo(REPO_DELETE));
        assertEquals(10, count);

        assertTotalApkCount(10);
        cursor = getMockContentResolver().query(
            ApkProvider.getRepoUri(REPO_DELETE), getMinimalProjection(), null, null, null);
        assertResultCount(0, cursor);

        // The only remaining apks should be those from REPO_KEEP.
        assertBelongsToRepo(queryAllApks(), REPO_KEEP);
    }

    public void testQuery() {
        Cursor cursor = queryAllApks();
        assertNotNull(cursor);
    }

    public void testInsert() {

        // Start with an empty database...
        Cursor cursor = queryAllApks();
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());

        Apk apk = new MockApk("com.phonemetra.turbo.store", 13);

        // Insert a new record...
        Uri newUri = TestUtils.insertApk(this, apk.id, apk.vercode);
        assertEquals(ApkProvider.getContentUri(apk).toString(), newUri.toString());
        cursor = queryAllApks();
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());

        // We intentionally throw an IllegalArgumentException if you haven't
        // yet called cursor.move*()...
        try {
            new Apk(cursor);
            fail();
        } catch (IllegalArgumentException e) {
            // Success!
        } catch (Exception e) {
            fail();
        }

        // And now we should be able to recover these values from the apk
        // value object (because the queryAllApks() helper asks for VERSION_CODE and
        // APK_ID.
        cursor.moveToFirst();
        Apk toCheck = new Apk(cursor);
        assertEquals("com.phonemetra.turbo.store", toCheck.id);
        assertEquals(13, toCheck.vercode);
    }

    public void testCount() {
        String[] projectionFields = getMinimalProjection();
        String[] projectionCount = new String[] { ApkProvider.DataColumns._COUNT };

        for (int i = 0; i < 13; i ++) {
            TestUtils.insertApk(this, "com.example", i);
        }

        Uri all = ApkProvider.getContentUri();
        Cursor allWithFields = getMockContentResolver().query(all, projectionFields, null, null, null);
        Cursor allWithCount = getMockContentResolver().query(all, projectionCount, null, null, null);

        assertResultCount(13, allWithFields);
        assertResultCount(1, allWithCount);

        allWithCount.moveToFirst();
        int countColumn = allWithCount.getColumnIndex(ApkProvider.DataColumns._COUNT);
        assertEquals(13, allWithCount.getInt(countColumn));
    }

    public void testInsertWithExtraFields() {

        assertResultCount(0, queryAllApks());

        String[] repoFields = new String[] {
            RepoProvider.DataColumns.DESCRIPTION,
            RepoProvider.DataColumns.ADDRESS,
            RepoProvider.DataColumns.FINGERPRINT,
            RepoProvider.DataColumns.NAME,
            RepoProvider.DataColumns.PUBLIC_KEY
        };

        for (String field : repoFields) {
            ContentValues invalidRepo = new ContentValues();
            invalidRepo.put(field, "Test data");
            try {
                TestUtils.insertApk(this, "com.phonemetra.turbo.store", 10, invalidRepo);
                fail();
            } catch (IllegalArgumentException e) {
            } catch (Exception e) {
                fail();
            }
            assertResultCount(0, queryAllApks());
        }

        ContentValues values = new ContentValues();
        values.put(ApkProvider.DataColumns.REPO_ID, 10);
        values.put(ApkProvider.DataColumns.REPO_ADDRESS, "http://example.com");
        values.put(ApkProvider.DataColumns.REPO_VERSION, 3);
        values.put(ApkProvider.DataColumns.FEATURES, "Some features");
        Uri uri = TestUtils.insertApk(this, "com.example.com", 1, values);

        assertResultCount(1, queryAllApks());

        String[] projections = ApkProvider.DataColumns.ALL;
        Cursor cursor = getMockContentResolver().query(uri, projections, null, null, null);
        cursor.moveToFirst();
        Apk apk = new Apk(cursor);

        // These should have quietly been dropped when we tried to save them,
        // because the provider only knows how to query them (not update them).
        assertEquals(null, apk.repoAddress);
        assertEquals(0, apk.repoVersion);

        // But this should have saved correctly...
        assertEquals("Some features", apk.features.toString());
        assertEquals("com.example.com", apk.id);
        assertEquals(1, apk.vercode);
        assertEquals(10, apk.repo);
    }

}
