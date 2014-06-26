/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.util.event;

import uk.co.real_logic.aeron.util.IoUtil;
import uk.co.real_logic.aeron.util.concurrent.AtomicBuffer;
import uk.co.real_logic.aeron.util.concurrent.ringbuffer.ManyToOneRingBuffer;
import uk.co.real_logic.aeron.util.concurrent.ringbuffer.RingBufferDescriptor;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.util.function.Consumer;

/**
 * Event Log Reader
 */
public class EventReader implements AutoCloseable
{
    private File eventsFile;
    private MappedByteBuffer buffer;
    private ManyToOneRingBuffer ringBuffer;

    public EventReader(final Context context)
    {
        try
        {
            eventsFile = context.eventsFile();

            if (eventsFile.exists())
            {
                System.err.println("WARNING: using existing event buffer at: " + eventsFile);
                buffer = IoUtil.mapExistingFile(eventsFile, "event-buffer");
            }
            else
            {
                buffer = IoUtil.mapNewFile(eventsFile, context.size());
            }

            if (context.deleteOnExit())
            {
                eventsFile.deleteOnExit();
            }

            ringBuffer = new ManyToOneRingBuffer(new AtomicBuffer(buffer));
        }
        catch (final Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public int read(final Consumer<String> handler)
    {
        return read(handler, Integer.MAX_VALUE);
    }

    public int read(final Consumer<String> handler, final int limit)
    {
        return ringBuffer.read(
            (typeId, buffer, index, length) ->
                handler.accept(EventCode.get(typeId).decode(buffer, index, length)), limit);
    }

    public void close() throws Exception
    {
        IoUtil.unmap(buffer);
    }

    public static class Context
    {
        private File eventsFile = new File(System.getProperty(EventConfiguration.LOCATION_PROPERTY_NAME,
            EventConfiguration.LOCATION_DEFAULT));
        private long bufferSize = Long.getLong(EventConfiguration.BUFFER_SIZE_PROPERTY_NAME,
            EventConfiguration.BUFFER_SIZE_DEFAULT) + RingBufferDescriptor.TRAILER_LENGTH;
        private boolean deleteOnExit = Boolean.getBoolean(EventConfiguration.DELETE_ON_EXIT_PROPERTY_NAME);

        public Context eventsFile(final File eventsFile)
        {
            this.eventsFile = eventsFile;
            return this;
        }

        public Context size(final long size)
        {
            this.bufferSize = size;
            return this;
        }

        public Context deleteOnExit(final boolean deleteOnExit)
        {
            this.deleteOnExit = deleteOnExit;
            return this;
        }

        public File eventsFile()
        {
            return eventsFile;
        }

        public long size()
        {
            return bufferSize;
        }

        public boolean deleteOnExit()
        {
            return deleteOnExit;
        }
    }
}
