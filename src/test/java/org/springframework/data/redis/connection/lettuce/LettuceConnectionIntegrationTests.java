/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.redis.connection.lettuce;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.springframework.data.redis.SpinBarrier.waitFor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.RedisVersionUtils;
import org.springframework.data.redis.SettingsUtils;
import org.springframework.data.redis.TestCondition;
import org.springframework.data.redis.connection.AbstractConnectionIntegrationTests;
import org.springframework.data.redis.connection.DefaultStringRedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.RedisStringCommands.BitOperation;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Integration test of {@link LettuceConnection}
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class LettuceConnectionIntegrationTests extends AbstractConnectionIntegrationTests {

	@Test
	@IfProfileValue(name = "runLongTests", value = "true")
	public void testMultiThreadsOneBlocking() throws Exception {
		Thread th = new Thread(new Runnable() {
			public void run() {
				DefaultStringRedisConnection conn2 = new DefaultStringRedisConnection(
						connectionFactory.getConnection());
				conn2.openPipeline();
				conn2.bLPop(3, "multilist");
				conn2.closePipeline();
				conn2.close();
			}
		});
		th.start();
		Thread.sleep(1000);
		connection.set("heythere", "hi");
		th.join();
		assertEquals("hi", connection.get("heythere"));
	}

	@Test
	public void testMultiConnectionsOneInTx() throws Exception {
		connection.set("txs1", "rightnow");
		connection.multi();
		connection.set("txs1", "delay");
		DefaultStringRedisConnection conn2 = new DefaultStringRedisConnection(
				connectionFactory.getConnection());

		// We get immediate results executing command in separate conn (not part
		// of tx)
		conn2.set("txs2", "hi");
		assertEquals("hi", conn2.get("txs2"));

		// Transactional value not yet set
		assertEquals("rightnow", conn2.get("txs1"));
		connection.exec();

		// Now it should be set
		assertEquals("delay", conn2.get("txs1"));
	}

	@Ignore("DATAREDIS-189 Lettuce exec without multi times out waiting for a reply instead of throwing Exception")
	@Test
	public void testCloseInTransaction() {
		connection.multi();
		connection.close();
		try {
			connection.exec();
			fail("Expected exception resuming tx");
		} catch (RedisSystemException e) {
			// expected, can't resume tx after closing conn
		}
		// can do normal ops after closing
		connection.get("txclose");

		// can complete a new tx after closing
		connection.multi();
		connection.set("txclose", "bar");
		List<Object> results = connection.exec();
		assertEquals("OK", results.get(0));
	}

	@Test
	public void testCloseBlockingOps() {
		connection.lPush("what", "baz");
		connection.bLPop(1, "what".getBytes());
		connection.close();

		// can do blocking ops after closing
		connection.lPush("what", "boo");
		connection.bLPop(1, "what".getBytes());

		// we can do regular ops
		connection.get("somekey");

		// we can start a tx
		connection.multi();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testSelect() {
		connection.select(1);
	}

	@Test(expected=UnsupportedOperationException.class)
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testBitOpNotMultipleSources() {
		connection.set("key1", "abcd");
		connection.set("key2", "efgh");
		actual.add(connection.bitOp(BitOperation.NOT, "key3", "key1", "key2"));
	}

	@Test
	@IfProfileValue(name = "runLongTests", value = "true")
	public void testScriptKill() throws Exception{
		getResults();
		assumeTrue(RedisVersionUtils.atLeast("2.6", byteConnection));
		final AtomicBoolean scriptDead = new AtomicBoolean(false);
		Thread th = new Thread(new Runnable() {
			public void run() {
				final LettuceConnectionFactory factory2 = new LettuceConnectionFactory(SettingsUtils.getHost(),
						SettingsUtils.getPort());
				factory2.afterPropertiesSet();
				DefaultStringRedisConnection conn2 = new DefaultStringRedisConnection(
						factory2.getConnection());
				try {
					conn2.eval("local time=1 while time < 10000000000 do time=time+1 end", ReturnType.BOOLEAN, 0);
				}catch(DataAccessException e) {
					scriptDead.set(true);
				}
				conn2.close();
			}
		});
		th.start();
		Thread.sleep(1000);
		connection.scriptKill();
		assertTrue(waitFor(new TestCondition() {
			public boolean passes() {
				return scriptDead.get();
			}
		}, 3000l));
	}
}