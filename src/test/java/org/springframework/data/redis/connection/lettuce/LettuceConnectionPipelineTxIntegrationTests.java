package org.springframework.data.redis.connection.lettuce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Test;
import org.springframework.data.redis.connection.RedisPipelineException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.test.annotation.IfProfileValue;

/**
 * Integration test of {@link LettuceConnection} transactions within a pipeline
 *
 * @author Jennifer Hickey
 *
 */
public class LettuceConnectionPipelineTxIntegrationTests extends
		LettuceConnectionTransactionIntegrationTests {

	@Test(expected = RedisPipelineException.class)
	public void exceptionExecuteNative() throws Exception {
		connection.execute("ZadD", getClass() + "#foo\t0.90\titem");
		getResults();
	}

	@Test
	public void testDbSize() {
		connection.set("dbparam", "foo");
		assertNull(connection.dbSize());
		List<Object> results = getResults();
		assertEquals(3, results.size());
		assertTrue((Long) results.get(2) > 0);
	}

	@Test
	public void testInfo() throws Exception {
		assertNull(connection.info());
		List<Object> results = getResults();
		assertEquals(2, results.size());
		Properties info = LettuceUtils.info((String) results.get(1));
		assertTrue("at least 5 settings should be present", info.size() >= 5);
		String version = info.getProperty("redis_version");
		assertNotNull(version);
	}

	@Test(expected=RedisPipelineException.class)
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testRestoreBadData() {
		// Use something other than dump-specific serialization
		connection.restore("testing".getBytes(), 0, "foo".getBytes());
		getResults();
	}

	@Test
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testRestoreExistingKey() {
		connection.set("testing", "12");
		connection.dump("testing".getBytes());
		List<Object> results = getResults();
		initConnection();
		connection.restore("testing".getBytes(), 0, (byte[]) results.get(2));
		try {
			getResults();
			fail("Expected RedisPipelineException restoring existing key");
		}catch(RedisPipelineException e) {
		}
	}

	@Test(expected=RedisPipelineException.class)
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testEvalShaNotFound() {
		connection.evalSha("somefakesha", ReturnType.VALUE, 2, "key1", "key2");
		getResults();
	}

	@Test(expected=RedisPipelineException.class)
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testEvalReturnSingleError() {
		connection.eval("return redis.call('expire','foo')", ReturnType.BOOLEAN, 0);
		getResults();
	}

	@Test
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testEvalReturnSingleOK() {
		actual.add(connection.eval("return redis.call('set','abc','ghk')", ReturnType.STATUS, 0));
		// We pipeline the result of multi, so add an extra OK here
		assertEquals(Arrays.asList(new Object[] { "OK", "OK" }), convertResults(false));
	}

	@Test
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testEvalReturnArrayOKs() {
		actual.add(connection.eval("return { redis.call('set','abc','ghk'),  redis.call('set','abc','lfdf')}",
				ReturnType.MULTI, 0));
		// We pipeline the result of multi, so add an extra OK here
		assertEquals(Arrays.asList(new Object[] {"OK", Arrays.asList(new Object[] {"OK", "OK"} )}), convertResults(false));
	}

	protected void initConnection() {
		connection.openPipeline();
		connection.multi();
	}

	protected List<Object> getResults() {
		assertNull(connection.exec());
		return connection.closePipeline();
	}

}
