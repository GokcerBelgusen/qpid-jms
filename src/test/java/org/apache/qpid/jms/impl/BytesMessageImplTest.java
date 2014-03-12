/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.jms.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javax.jms.BytesMessage;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import org.apache.qpid.jms.QpidJmsTestCase;
import org.apache.qpid.jms.engine.AmqpBytesMessage;
import org.apache.qpid.jms.engine.AmqpConnection;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.message.Message;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class BytesMessageImplTest extends QpidJmsTestCase
{
    private static final int END_OF_STREAM = -1;

    private Delivery _mockDelivery;
    private ConnectionImpl _mockConnectionImpl;
    private SessionImpl _mockSessionImpl;
    private AmqpConnection _mockAmqpConnection;

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _mockAmqpConnection = Mockito.mock(AmqpConnection.class);
        _mockConnectionImpl = Mockito.mock(ConnectionImpl.class);
        _mockSessionImpl = Mockito.mock(SessionImpl.class);
        Mockito.when(_mockSessionImpl.getDestinationHelper()).thenReturn(new DestinationHelper());
    }

    @Test
    public void testGetBodyLengthUsingReceivedMessageWithNoBodySection() throws Exception
    {
        Message message = Proton.message();
        AmqpBytesMessage testAmqpMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(testAmqpMessage, _mockSessionImpl,_mockConnectionImpl, null);

        assertEquals(0, bytesMessageImpl.getBodyLength());
    }

    /**
     * Test that calling {@link BytesMessage#getBodyLength()} on a new message which has been
     * populated and {@link BytesMessage#reset()} causes the length to be reported correctly.
     */
    @Test
    public void testResetOnNewlyPopulatedBytesMessageUpdatesBodyLength() throws Exception
    {
        byte[] bytes = "newResetTestBytes".getBytes();

        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(_mockSessionImpl,_mockConnectionImpl);

        bytesMessageImpl.writeBytes(bytes);
        bytesMessageImpl.reset();
        assertEquals("Message reports unexpected length", bytes.length, bytesMessageImpl.getBodyLength());
    }

    /**
     * Test that attempting to call {@link BytesMessage#getBodyLength()} on a new message causes a
     * {@link MessageNotReadableException} to be thrown due to being write-only.
     */
    @Test
    public void testGetBodyLengthOnNewMessageThrowsMessageNotReadableException() throws Exception
    {
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(_mockSessionImpl,_mockConnectionImpl);

        try
        {
            bytesMessageImpl.getBodyLength();
            fail("expected exception to be thrown");
        }
        catch(MessageNotReadableException mnre)
        {
            //expected
        }
    }

    @Test
    public void testReadBytesUsingReceivedMessageWithNoBodySectionReturnsEOS() throws Exception
    {
        Message message = Proton.message();
        AmqpBytesMessage testAmqpMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(testAmqpMessage, _mockSessionImpl,_mockConnectionImpl, null);

        //verify attempting to read bytes returns -1, i.e EOS
        assertEquals("Expected input stream to be at end but data was returned", END_OF_STREAM, bytesMessageImpl.readBytes(new byte[1]));
    }

    @Test
    public void testReadBytesUsingReceivedMessageWithDataSectionReturnsBytes() throws Exception
    {
        byte[] bytes = "myBytesData".getBytes();

        Message message = Proton.message();
        message.setBody(new Data(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        //retrieve the expected bytes, check they match
        byte[] receivedBytes = new byte[bytes.length];
        bytesMessageImpl.readBytes(receivedBytes);
        assertTrue(Arrays.equals(bytes, receivedBytes));

        //verify no more bytes remain, i.e EOS
        assertEquals("Expected input stream to be at end but data was returned", END_OF_STREAM, bytesMessageImpl.readBytes(new byte[1]));

        assertEquals("Message reports unexpected length", bytes.length, bytesMessageImpl.getBodyLength());
    }

    @Test
    public void testReadBytesUsingReceivedMessageWithAmpValueSectionReturnsBytes() throws Exception
    {
        byte[] bytes = "myBytesAmqpValue".getBytes();

        Message message = Proton.message();
        message.setBody(new AmqpValue(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        //retrieve the expected bytes, check they match
        byte[] receivedBytes = new byte[bytes.length];
        bytesMessageImpl.readBytes(receivedBytes);
        assertTrue(Arrays.equals(bytes, receivedBytes));

        //verify no more bytes remain, i.e EOS
        assertEquals("Expected input stream to be at end but data was returned", END_OF_STREAM, bytesMessageImpl.readBytes(receivedBytes));

        assertEquals("Message reports unexpected length", bytes.length, bytesMessageImpl.getBodyLength());
    }

    /**
     * Test that attempting to write bytes to a received message (without calling {@link BytesMessage#clearBody()} first)
     * causes a {@link MessageNotWriteableException} to be thrown due to being read-only.
     */
    @Test
    public void testReceivedBytesMessageThrowsMessageNotWriteableExceptionOnWriteBytes() throws Exception
    {
        byte[] bytes = "myBytesAmqpValue".getBytes();

        Message message = Proton.message();
        message.setBody(new AmqpValue(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        try
        {
            bytesMessageImpl.writeBytes(bytes);
            fail("expected exception to be thrown");
        }
        catch(MessageNotWriteableException mnwe)
        {
            //expected
        }
    }

    /**
     * Test that attempting to read bytes from a new message (without calling {@link BytesMessage#reset()} first) causes a
     * {@link MessageNotReadableException} to be thrown due to being write-only.
     */
    @Test
    public void testNewBytesMessageThrowsMessageNotReadableOnReadBytes() throws Exception
    {
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(_mockSessionImpl,_mockConnectionImpl);

        //retrieve the expected bytes, check they match
        byte[] receivedBytes = new byte[1];
        try
        {
            bytesMessageImpl.readBytes(receivedBytes);
            fail("expected exception to be thrown");
        }
        catch(MessageNotReadableException mnre)
        {
            //expected
        }
    }

    /**
     * Test that calling {@link BytesMessage#clearBody()} causes a received
     * message to become writable
     */
    @Test
    public void testClearBodyOnReceivedBytesMessageMakesMessageWritable() throws Exception
    {
        byte[] bytes = "myBytesAmqpValue".getBytes();

        Message message = Proton.message();
        message.setBody(new AmqpValue(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        assertFalse("Message should not be writable", bytesMessageImpl.isBodyWritable());

        bytesMessageImpl.clearBody();

        assertTrue("Message should be writable", bytesMessageImpl.isBodyWritable());
    }

    /**
     * Test that calling {@link BytesMessage#clearBody()} of a received message
     * causes the body of the underlying {@link AmqpBytesMessage} to be emptied.
     */
    @Test
    public void testClearBodyOnReceivedBytesMessageClearsUnderlyingMessageBody() throws Exception
    {
        byte[] bytes = "myBytesAmqpValue".getBytes();

        Message message = Proton.message();
        message.setBody(new AmqpValue(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        assertNotNull("Expected body section but none was present", message.getBody());

        bytesMessageImpl.clearBody();

        //check that the returned BAIS returns no data and reports 0 length
        ByteArrayInputStream bais = amqpBytesMessage.getByteArrayInputStream();
        assertEquals("Expected input stream to be at end but data was returned", END_OF_STREAM, bais.read(new byte[1]));
        assertEquals("Underlying message should report 0 length", 0, amqpBytesMessage.getBytesLength());

        //verify the underlying message has no body section
        //TODO: this test assumes we can omit the body section. If we decide otherwise
        //it should instead check for e.g. a data section containing 0 length binary
        assertNull("Expected no body section", message.getBody());
    }

    /**
     * Test that attempting to call {@link BytesMessage#getBodyLength()} on a received message after calling
     * {@link BytesMessage#clearBody()} causes {@link MessageNotReadableException} to be thrown due to being write-only.
     */
    @Test
    public void testGetBodyLengthOnClearedReceivedMessageThrowsMessageNotReadableException() throws Exception
    {
        byte[] bytes = "myBytesData".getBytes();

        Message message = Proton.message();
        message.setBody(new Data(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        assertEquals("Unexpected message length", bytes.length, bytesMessageImpl.getBodyLength());

        bytesMessageImpl.clearBody();

        try
        {
            bytesMessageImpl.getBodyLength();
            fail("expected exception to be thrown");
        }
        catch(MessageNotReadableException mnre)
        {
            //expected
        }
    }

    /**
     * Test that calling {@link BytesMessage#reset()} causes a write-only
     * message to become read-only
     */
    @Test
    public void testResetOnReceivedBytesMessageResetsMarker() throws Exception
    {
        byte[] bytes = "resetTestBytes".getBytes();

        Message message = Proton.message();
        message.setBody(new AmqpValue(new Binary(bytes)));

        AmqpBytesMessage amqpBytesMessage = new AmqpBytesMessage(_mockDelivery, message, _mockAmqpConnection);
        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(amqpBytesMessage, _mockSessionImpl,_mockConnectionImpl, null);

        //retrieve a few bytes, check they match the first few expected bytes
        byte[] partialBytes = new byte[3];
        bytesMessageImpl.readBytes(partialBytes);
        byte[] partialOriginalBytes = Arrays.copyOf(bytes, 3);
        assertTrue(Arrays.equals(partialOriginalBytes, partialBytes));

        bytesMessageImpl.reset();

        //retrieve all the expected bytes, check they match
        byte[] resetBytes = new byte[bytes.length];
        bytesMessageImpl.readBytes(resetBytes);
        assertTrue(Arrays.equals(bytes, resetBytes));
    }

    /**
     * Test that calling {@link BytesMessage#reset()} on a new message which has been
     * populated causes the marker to be reset and makes the message read-only
     */
    @Test
    public void testResetOnNewlyPopulatedBytesMessageResetsMarkerAndMakesReadable() throws Exception
    {
        byte[] bytes = "newResetTestBytes".getBytes();

        BytesMessageImpl bytesMessageImpl = new BytesMessageImpl(_mockSessionImpl,_mockConnectionImpl);

        assertTrue("Message should be writable", bytesMessageImpl.isBodyWritable());
        bytesMessageImpl.writeBytes(bytes);
        bytesMessageImpl.reset();
        assertFalse("Message should not be writable", bytesMessageImpl.isBodyWritable());

        //retrieve the bytes, check they match
        byte[] resetBytes = new byte[bytes.length];
        bytesMessageImpl.readBytes(resetBytes);
        assertTrue(Arrays.equals(bytes, resetBytes));
    }
}
