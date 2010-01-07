package com.clarkparsia.empire.test;

import com.clarkparsia.utils.collections.CollectionUtil;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.clarkparsia.empire.Empire;
import com.clarkparsia.empire.DataSource;
import com.clarkparsia.empire.EmpireOptions;
import com.clarkparsia.empire.jena.JenaInMemoryDataSourceFactory;
import com.clarkparsia.empire.sesame.SesameDataSourceFactory;

import com.clarkparsia.empire.test.api.TestDataSourceFactory;
import com.clarkparsia.empire.test.api.MutableTestDataSourceFactory;
import com.clarkparsia.empire.test.api.TestEntityListener;
import com.clarkparsia.empire.test.api.nasa.FoafPerson;
import com.clarkparsia.empire.test.api.nasa.SpaceVocab;
import com.clarkparsia.empire.test.api.nasa.Spacecraft;

import com.clarkparsia.empire.impl.EntityManagerFactoryImpl;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Query;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Collection;
import java.util.Arrays;

import java.net.URI;

import com.clarkparsia.sesame.utils.SesameValueFactory;

/**
 * Title: <br/>
 * Description: <br/>
 * Company: Clark & Parsia, LLC. <http://www.clarkparsia.com> <br/>
 * Created: Dec 29, 2009 3:20:34 PM <br/>
 *
 * @author Michael Grove <mike@clarkparsia.com>
 */
@RunWith(Parameterized.class)
public class TestJPA {
	public static final String DATA_FILE = System.getProperty("test.data") != null ? System.getProperty("test.data") : "test/data/nasa.nt";
	private static EntityManagerFactory mFactory;

    private EntityManager mManager;

	private String mTestQuery;
	private String mNativeQuery;
	private String mNativeFragment;
	private String mAgencyQuery;
	private String mAgencyWildcardQuery;
	private String mParameterizedQuery;
	private String mNamedQueryName;

	public TestJPA(final Map<String, String> theConfig, String theTestQuery, String theNativeQuery,
				   String theNativeQueryFragment, String theAgencyQuery, String theAgencyWildcardQuery,
				   String theParameterizedQuery, String theQueryName) {

		mManager = mFactory.createEntityManager(theConfig);
		mTestQuery = theTestQuery;
		mNativeQuery = theNativeQuery;
		mNativeFragment = theNativeQueryFragment;
		mAgencyQuery = theAgencyQuery;
		mAgencyWildcardQuery = theAgencyWildcardQuery;
		mParameterizedQuery = theParameterizedQuery;
		mNamedQueryName = theQueryName;
	}

	@Parameterized.Parameters
	public static Collection configurations() {
		return Arrays.asList(new Object[][] {
				{ getLocalSesameTestConfigMap(), "from {result} <urn:prop> {y}",
				  								 "select distinct result from {uri} <" + SpaceVocab.ontology().mass.getURI() + "> {result}",
												 "from {uri} <" + SpaceVocab.ontology().mass.getURI() + "> {result}",
												 "from {result} <" + SpaceVocab.ontology().agency + "> {\"U.S.S.R\"}",
												 "from {result} <" + SpaceVocab.ontology().agency + "> {??}",
												 "from {result} <" + SpaceVocab.ontology().agency + "> {??}, {result} <" + SpaceVocab.ontology().alternateName + "> {??altName}",
												 "sovietSpacecraft" },
// NOTE: don't forget to run TestUtil before testing 4store, the 4store db needs to be prepped.
//				{ getFourStoreTestConfigMap(), "where { ?result <urn:prop> ?y }",
//				  							   "select distinct ?result where { ?uri <" + SpaceVocab.ontology().mass.getURI() + "> ?result }",
//											   "where { ?uri <" + SpaceVocab.ontology().mass.getURI() + "> ?result }",
//											   "where { ?result <" + SpaceVocab.ontology().agency + "> \"U.S.S.R\" }",
//											   "where { ?result <" + SpaceVocab.ontology().agency + "> ?? }",
//											   "where { ?result <" + SpaceVocab.ontology().agency + "> ??. ?result <" + SpaceVocab.ontology().alternateName + "> ??altName }",
//											   "sovietSpacecraftSPARQL" }

				{ getLocalJenaTestConfigMap(), "where { ?result <urn:prop> ?y }",
				  							   "select distinct ?result where { ?uri <" + SpaceVocab.ontology().mass.getURI() + "> ?result }",
											   "where { ?uri <" + SpaceVocab.ontology().mass.getURI() + "> ?result }",
											   "where { ?result <" + SpaceVocab.ontology().agency + "> \"U.S.S.R\" }",
											   "where { ?result <" + SpaceVocab.ontology().agency + "> ?? }",
											   "where { ?result <" + SpaceVocab.ontology().agency + "> ??. ?result <" + SpaceVocab.ontology().alternateName + "> ??altName }",
											   "sovietSpacecraftSPARQL" }
		});
	}

	@BeforeClass
	public static void beforeClass() {
        // our test data set doesn't type any literals, so we have to set to weak (no) typing
        // TODO: don't hard code this if we're doing tests w/ other datasets.
        EmpireOptions.STRONG_TYPING = false;

		mFactory = new EntityManagerFactoryImpl();
	}

	@AfterClass
	public static void afterClass() {
		mFactory.close();

		Empire.close();
	}

	@Test
	public void testEntityManagerBasics() {
		EntityManager aManager = createEntityManager();

		assertTrue(aManager.isOpen());

		// we support this...
		aManager.setFlushMode(FlushModeType.AUTO);

		// ... but not this
		try {
			aManager.setFlushMode(FlushModeType.COMMIT);
			fail("IllegalArgumentException expected");
		}
		catch (IllegalArgumentException e) { /* expected */ }

		assertEquals(aManager.getFlushMode(), FlushModeType.AUTO);

		// should complete w/o error
		aManager.flush();
		aManager.clear();

		assertTrue(aManager.getDelegate() instanceof DataSource);
		assertTrue(aManager.getTransaction() != null);

		aManager.close();

		assertFalse(aManager.isOpen());
	}

	@Test @Ignore
	public void testTransactionSupport() {
		// TODO: implement test
	}

	@Test
	public void testFindAndContains() {
		Spacecraft aCraft = mManager.find(Spacecraft.class,
										  URI.create("http://nasa.dataincubator.org/spacecraft/1957-001A"));

		assertTrue(aCraft != null);

		// just a couple checks to see if this is the right space craft
		assertEquals(aCraft.getAgency(), "U.S.S.R");
		assertTrue(CollectionUtil.contentsEqual(aCraft.getAlternateName(), Collections.singletonList("00001")));
		assertEquals(aCraft.getHomepage(), URI.create("http://nssdc.gsfc.nasa.gov/database/MasterCatalog?sc=1957-001A"));

		assertTrue(mManager.contains(aCraft));

		assertFalse(mManager.contains(new Spacecraft(URI.create("http://nasa.dataincubator.org/spacecraft/fakeSpacecraft"))));

		Spacecraft aCopy = mManager.find(Spacecraft.class, URI.create("http://nasa.dataincubator.org/spacecraft/1957-001A"));

		assertEquals(aCraft, aCopy);

		assertTrue(null == mManager.find(Spacecraft.class, URI.create("http://nasa.dataincubator.org/spacecraft/doesNotExist")));

		aCopy = mManager.getReference(Spacecraft.class, URI.create("http://nasa.dataincubator.org/spacecraft/1957-001A"));

		assertEquals(aCopy, aCraft);
	}

	@Test(expected=IllegalStateException.class)
	public void testGetReferenceWhenClosed() {
		createClosedEntityManager().getReference(Spacecraft.class, URI.create("urn:find:me"));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testGetReferenceWithInvalidPk() {
		createEntityManager().getReference(FoafPerson.class, "{invalid}");
	}

	@Test(expected=IllegalArgumentException.class)
	public void testGetReferenceWithNullPk() {
		createEntityManager().getReference(FoafPerson.class, null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testContainsWithNullParam() {
		createEntityManager().contains(null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testContainsWithInvalidParam() {
		createEntityManager().contains(42);
	}

	@Test(expected=IllegalStateException.class)
	public void testContainsWhenClosed() {
		createClosedEntityManager().contains(URI.create("urn:find:me"));
	}

	@Test(expected=IllegalStateException.class)
	public void testFindWhenClosed() {
		createClosedEntityManager().find(Spacecraft.class, URI.create("urn:find:me"));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testFindWithInvalidPk() {
		createEntityManager().find(FoafPerson.class, new Date());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testFindWithNullPk() {
		createEntityManager().find(FoafPerson.class, null);
	}

	@Test
	public void testPreAndPostHooks() {
		TestEntityListener.clearState();

		Spacecraft aCraft = mManager.find(Spacecraft.class,
										  URI.create("http://nasa.dataincubator.org/spacecraft/1989-033B"));

		assertTrue(aCraft.postLoadCalled);
		assertTrue(TestEntityListener.postLoadCalled);

		TestEntityListener.clearState();
		aCraft.clearState();

		aCraft.setDescription("some new description");

		aCraft = mManager.merge(aCraft);

		// TODO: this fails because it's not the same Craft object, i don't know if it's safe to assume it should be.
		assertTrue(aCraft.preUpdateCalled);
		assertTrue(aCraft.postUpdateCalled);
		assertTrue(TestEntityListener.preUpdateCalled);
		assertTrue(TestEntityListener.postUpdateCalled);

		TestEntityListener.clearState();
		aCraft.clearState();

		mManager.remove(aCraft);

		assertTrue(aCraft.preRemoveCalled);
		assertTrue(aCraft.postRemoveCalled);
		assertTrue(TestEntityListener.preRemoveCalled);
		assertTrue(TestEntityListener.postRemoveCalled);

		TestEntityListener.clearState();
		aCraft.clearState();

        Spacecraft aNewCraft = new Spacecraft();

        aNewCraft.setAgency("U.S.A");
        aNewCraft.setAlternateName(Collections.singletonList("67890"));
        aNewCraft.setDescription("The newer rocket to return to the moon");
        aNewCraft.setName("Ares 2");
        aNewCraft.setMass("5000");

		mManager.persist(aNewCraft);

		assertTrue(aCraft.prePersistCalled);
		assertTrue(aCraft.postPersistCalled);
		assertTrue(TestEntityListener.prePersistCalled);
		assertTrue(TestEntityListener.postPersistCalled);
	}

	@Test
	public void testPersist() {
        Spacecraft aNewCraft = new Spacecraft();

        aNewCraft.setAgency("USA");
        aNewCraft.setAlternateName(Collections.singletonList("12345"));
        aNewCraft.setDescription("The new rocket to return to the moon");
        aNewCraft.setName("Ares 1");
        aNewCraft.setMass("1000");

        mManager.persist(aNewCraft);

        Spacecraft aCraft = mManager.find(Spacecraft.class,
                                          aNewCraft.getRdfId());

        assertEquals(aNewCraft, aCraft);
	}

    @Test(expected=IllegalArgumentException.class)
    public void testPersistWithInvalidObj() {
        mManager.persist("not valid");
    }

    @Test(expected=EntityExistsException.class)
    public void testPersistExistingObj() {
        Spacecraft aCraft = mManager.find(Spacecraft.class,
                                          URI.create("http://nasa.dataincubator.org/spacecraft/1957-001A"));

        mManager.persist(aCraft);
    }

    @Test(expected=IllegalStateException.class)
    public void testPersistWhenClosed() {
        createClosedEntityManager().persist(new Spacecraft());
    }

	@Test
	public void testRemove() {
        Spacecraft aCraft = mManager.find(Spacecraft.class,
                                          URI.create("http://nasa.dataincubator.org/spacecraft/1957-001B"));

        mManager.remove(aCraft);

        assertFalse(mManager.contains(aCraft));
	}

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveWithInvalidObj() {
        mManager.remove("not valid");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveNonExistingObj() {
        Spacecraft aCraft = new Spacecraft();
        aCraft.setName("Not in the database");

        mManager.remove(aCraft);
    }

    @Test(expected=IllegalStateException.class)
    public void testRemoveWhenClosed() {
        createClosedEntityManager().persist(new Spacecraft());
    }

	@Test
	public void testUpdate() {
		Spacecraft aCraft = mManager.find(Spacecraft.class,
										  URI.create("http://nasa.dataincubator.org/spacecraft/1957-002A"));

		String aNewAgency = "America";
		URI aNewHomepage = URI.create("http://nasa.gov");
		String aNewMass = "12345";

		aCraft.setMass(aNewMass);
		aCraft.setAgency(aNewAgency);
		aCraft.setHomepage(aNewHomepage);

		Spacecraft aUpdatedCraft = mManager.merge(aCraft);

		assertEquals(aUpdatedCraft.getAgency(), aNewAgency);
		assertEquals(aUpdatedCraft.getHomepage(), aNewHomepage);
		assertEquals(aUpdatedCraft.getMass(), aNewMass);

		assertEquals(aUpdatedCraft, aCraft);
	}

	@Test
	public void testRefresh() {
		Spacecraft aCraft = mManager.find(Spacecraft.class,
										  URI.create("http://nasa.dataincubator.org/spacecraft/1957-002A"));

		String aNewAgency = "America";
		URI aNewHomepage = URI.create("http://nasa.gov");
		String aNewMass = "12345";

		aCraft.setMass(aNewMass);
		aCraft.setAgency(aNewAgency);
		aCraft.setHomepage(aNewHomepage);

		mManager.refresh(aCraft);

		assertFalse(aCraft.getAgency().equals(aNewAgency));
		assertFalse(aCraft.getHomepage().equals(aNewHomepage));
		assertFalse(aCraft.getMass().equals(aNewMass));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRefreshWithNull() {
		mManager.refresh(null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRefreshWithInvalidObj() {
		mManager.refresh(new Date());
	}

	@Test
	public void testQueries() {
	 	Query aQuery = mManager.createQuery(mTestQuery);

        // setting a hint should work, though it does not do anything
        aQuery.setHint("test", new Object());

        aQuery.setFlushMode(FlushModeType.AUTO);

        try {
            aQuery.setFlushMode(FlushModeType.COMMIT);
            fail("Should not be able to set the flush mode to commit");
        }
        catch (Exception e) {
            // expected
        }
    }

	@Test
	public void testQuerying() {
        // TODO: get serql or sparql depending on supported dialect
        String aNativeQueryStr = mNativeQuery;
        String aQueryStr = mNativeFragment;

		Query aNativeQuery = mManager.createNativeQuery(aNativeQueryStr);
        Query aQuery = mManager.createQuery(aQueryStr);

        // both of these query objects should return the same result set
        List aResults = aNativeQuery.getResultList();

        assertTrue(CollectionUtil.contentsEqual(aResults, aQuery.getResultList()));

        aNativeQuery = mManager.createNativeQuery(aNativeQueryStr, String.class);
        aResults = aNativeQuery.getResultList();
        for (Object aResult : aResults) {
            assertTrue(aResult instanceof String);
        }

        aNativeQuery.setMaxResults(10);
        aResults = aNativeQuery.getResultList();

        assertEquals(aResults.size(), 10);

        aNativeQuery.setFirstResult(13);
        aResults = aNativeQuery.getResultList();
        List aResultsCopy = aNativeQuery.getResultList();

        assertTrue(CollectionUtil.contentsEqual(aResults, aResultsCopy));

        assertEquals(aQuery, aQuery);
        assertFalse(aQuery.equals(aNativeQuery));

        aNativeQuery = mManager.createNativeQuery(mAgencyQuery);
		
        aQuery = mManager.createNativeQuery(mAgencyWildcardQuery);

        // creating a value object here because our dataset doesnt type any literals, so the automatic typing of a
        // plain string, or double or whatever (string in this case) will not return results, so we have to do it the long way.
        aQuery.setParameter(1, SesameValueFactory.instance().createLiteral("U.S.S.R"));

        aResults = aQuery.getResultList();

        assertTrue(CollectionUtil.contentsEqual(aResults, aNativeQuery.getResultList()));

		// get an equivalent named query and make sure that the results are equal
		assertTrue(CollectionUtil.contentsEqual(mManager.createNamedQuery(mNamedQueryName).getResultList(), aResults));

        try {
            aQuery.getSingleResult();
            fail("NonUniqueException expected");
        }
        catch (NonUniqueResultException e) {
            // this is what we'd expect
        }

        aQuery.setParameter(1, SesameValueFactory.instance().createLiteral("zjlkdiouasdfuoi"));

        try {
            aQuery.getSingleResult();
            fail("NoResultException expected");
        }
        catch (NoResultException e) {
            // this is what we'd expect
        }

        aQuery = mManager.createNativeQuery(mParameterizedQuery,
                                            Spacecraft.class);

        aQuery.setParameter(1, SesameValueFactory.instance().createLiteral("U.S.S.R"));
        aQuery.setParameter("altName", SesameValueFactory.instance().createLiteral("00001"));

        Object aObj = aQuery.getSingleResult();

        assertTrue(aObj != null);
        assertTrue(aObj instanceof Spacecraft);

        Spacecraft aCraft = (Spacecraft) aObj;

        assertEquals(aCraft.getAgency(), "U.S.S.R");
        assertEquals(aCraft.getAlternateName(), Collections.singletonList("00001"));
    }

	@Test @Ignore
	public void testLocking() {
		// TODO: devise a test for the locking stuff...once it's supported
	}

	@Test
	public void testEmpire() {
		Empire.close();

		assertFalse(Empire.isInitialized());

		try {
			Empire.em();
			fail("IllegalStateException expected");
		}
		catch (IllegalStateException e) {
			// this is what is expected
		}

		EntityManager aEM = createEntityManager();

		Empire.create(aEM);

		assertTrue(Empire.isInitialized());

		assertEquals(aEM, Empire.em());
	}

	@Test
	public void testEntityManagerFactory() {
		EntityManagerFactory aFac = new EntityManagerFactoryImpl();

		assertTrue(aFac.isOpen());

		EntityManager aManager = aFac.createEntityManager(getTestEMConfigMap());

		aFac.close();

		assertFalse(aFac.isOpen());
		assertFalse(aManager.isOpen());

		try {
			aFac.createEntityManager(getTestEMConfigMap());
			fail("IllegalStateException expected");
		}
		catch (IllegalStateException ex) {
			// expected
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testInvalidFactory() {
		mFactory.createEntityManager(Collections.singletonMap(EntityManagerFactoryImpl.FACTORY, "Not.a.class.name"));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNoFactory() {
		mFactory.createEntityManager();
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNotMutableDataSource() {
		mFactory.createEntityManager(Collections.singletonMap(EntityManagerFactoryImpl.FACTORY,
															  TestDataSourceFactory.class.getName()));
	}

	@Test(expected=IllegalStateException.class)
	public void testCloseWhenClosed() {
		createClosedEntityManager().close();
	}

	@Test(expected=IllegalStateException.class)
	public void testSetFlushModeWhenClosed() {
		createClosedEntityManager().setFlushMode(FlushModeType.AUTO);
	}

	@Test(expected=IllegalStateException.class)
	public void testGetFlushModeWhenClosed() {
		createClosedEntityManager().getFlushMode();
	}
	@Test(expected=IllegalStateException.class)
	public void testClearWhenClosed() {
		createClosedEntityManager().clear();
	}

	@Test(expected=IllegalStateException.class)
	public void testFlushWhenClosed() {
		createClosedEntityManager().flush();
	}

	@Test(expected=IllegalStateException.class)
	public void testJoinTransactionWhenClosed() {
		createClosedEntityManager().joinTransaction();
	}

	private EntityManager createEntityManager() {
		return mFactory.createEntityManager(getTestEMConfigMap());
	}

	private EntityManager createClosedEntityManager() {
		EntityManager aManager = createEntityManager();
		aManager.close();

		return aManager;
	}

	private static Map<String, String> getTestEMConfigMap() {
		Map<String, String> aMap = new HashMap<String, String>();

		aMap.put(EntityManagerFactoryImpl.FACTORY, SesameDataSourceFactory.class.getName());
		aMap.put(SesameDataSourceFactory.REPO, "test-repo");

		return aMap;
	}

	private static Map<String, String> getLocalSesameTestConfigMap() {
		Map<String, String> aMap = new HashMap<String, String>();

		aMap.put(EntityManagerFactoryImpl.FACTORY, MutableTestDataSourceFactory.class.getName());

		aMap.put("files", DATA_FILE);

		return aMap;
	}

	private static Map<String, String> getLocalJenaTestConfigMap() {
		Map<String, String> aMap = new HashMap<String, String>();

		aMap.put(EntityManagerFactoryImpl.FACTORY, JenaInMemoryDataSourceFactory.class.getName());

		aMap.put("files", DATA_FILE);

		return aMap;
	}

//	private static Map<String, String> getFourStoreTestConfigMap() {
//		Map<String, String> aMap = new HashMap<String, String>();
//
//		aMap.put(EntityManagerFactoryImpl.FACTORY, FourStoreDataSourceFactory.class.getName());
//
//		aMap.put(FourStoreDataSourceFactory.KEY_URL, "http://localhost:4040");
//
//		return aMap;
//	}
}