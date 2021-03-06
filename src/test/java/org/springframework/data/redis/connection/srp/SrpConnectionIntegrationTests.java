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

package org.springframework.data.redis.connection.srp;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.redis.connection.AbstractConnectionIntegrationTests;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import redis.reply.BulkReply;

/**
 * Integration test of {@link SrpConnection}
 *
 * @author Costin Leau
 * @author Jennifer Hickey
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SrpConnectionIntegrationTests extends AbstractConnectionIntegrationTests {

	@After
	public void tearDown() {
		try {
			connection.flushDb();
		} catch (Exception e) {
			// SRP doesn't allow other commands to be executed once subscribed,
			// so
			// this fails after pub/sub tests
		}
		connection.close();
		connection = null;
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testZInterStoreAggWeights() {
		super.testZInterStoreAggWeights();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testZUnionStoreAggWeights() {
		super.testZUnionStoreAggWeights();
	}

	@Test
	@IfProfileValue(name = "redisVersion", values = {"2.4", "2.6"})
	public void testGetRangeSetRange() {
		super.testGetRangeSetRange();
	}

	@Test
	public void testExecute() {
		connection.set("foo", "bar");
		BulkReply reply = (BulkReply) connection.execute("GET", "foo");
		assertEquals("bar", stringSerializer.deserialize(reply.data()));
	}

	@Test
	@IfProfileValue(name = "redisVersion", value = "2.6")
	public void testEvalReturnArrayOKs() {
		// SRP returns the Strings from individual StatusReplys in a MultiBulkReply, while other clients return as byte[]
		actual.add(connection.eval("return { redis.call('set','abc','ghk'),  redis.call('set','abc','lfdf')}",
				ReturnType.MULTI, 0));
		verifyResults(Arrays.asList(new Object[] {Arrays.asList(new Object[] {"OK", "OK"} )}), actual);
	}

	@Ignore("https://github.com/spullara/redis-protocol/issues/25")
	public void testScriptExists() {
		//script_exists only returns one result and it's false
	}
}
