package com.overit.tomcat.redis;

import org.apache.catalina.Executor;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import com.overit.tomcat.TesterContext;
import com.overit.tomcat.TesterServletContext;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.*;

public class RedisStoreTest {
    private RedisStore store;
    private Manager manager;

    @Before
    public void setUp() throws Exception {
        TesterContext testerContext = new TesterContext();
        testerContext.setServletContext(new TesterServletContext());
        manager = new StandardManager();
        manager.setContext(testerContext);
        store = new RedisStore();
        store.setUrl(String.format("redis://%s:%d", "localhost", 6379));
        store.setManager(manager);

        store.save(createSession("s1"));
        store.save(createSession("s2"));
    }

    @After
    public void cleanup() throws Exception {
        store.clear();
        store.stop();
    }

    private Session createSession(String id) {
        StandardSession session = new StandardSession(manager);
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(manager.getContext().getSessionTimeout() * 60);
        session.setAttribute("key", "val");
        session.setId(id);
        return session;
    }

    @Test
    public void getSize() {
        assertEquals(2, store.getSize());
    }

    @Test
    public void clear() {
        store.clear();
        assertEquals(0, store.getSize());
    }

    @Test
    public void keys() {
        assertArrayEquals(new String[]{"s1", "s2"}, store.keys());
        store.clear();
        assertArrayEquals(new String[]{}, store.keys());
    }

    @Test
    public void remove() {
        store.remove("s1");
        assertEquals(1, store.getSize());
    }

    @Test
    public void add() throws IOException {
        store.save(createSession("s3"));
        assertEquals(3, store.getSize());
        assertArrayEquals(new String[]{"s1", "s2", "s3"}, store.keys());
    }

    @Test
    public void load() {
        Session s2 = store.load("s2");
        assertNotNull(s2);
        assertEquals("s2", s2.getIdInternal());
    }

    @Test
    public void load_havingNoStoredSessionAndNoOneCanDrainIt_shouldReturnNull() {
        long start = System.currentTimeMillis();

        Session session = store.load("unknown");

        long now = System.currentTimeMillis();

        assertThat(session).isNull();
        assertThat(now-start).isCloseTo(2000, Offset.offset(500L));
    }

    @Test
    public void load_havingNoStoredSessionAndItIsDrainedLater_shouldReturnNotNull() throws IOException {
        String key = "tomcat:session:s4:requested";

        RedisConnector.instance().execute(j -> j.set(key, "true"));
        Executors.newFixedThreadPool(1).submit(() -> {
            try {
                Thread.sleep(1000);
                store.save(createSession("s4"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        Session session = store.load("s4");

        assertThat(session).isNotNull();
    }
}