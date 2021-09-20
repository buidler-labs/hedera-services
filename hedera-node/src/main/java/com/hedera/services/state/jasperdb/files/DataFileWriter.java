package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.*;

/**
 * Writer for creating a data file. A data file contains a number of data items. Each data item can be variable or fixed
 * size and is considered as a black box. All access to contents of the data item is done via the DataItemSerializer.
 * <p>
 * <b>This is designed to be used from a single thread.</b>
 * <p>
 * At the end of the file it is padded till a 4096 byte page boundary then a footer page is written by DataFileMetadata.
 *
 * @param <D> Data item type
 */
public final class DataFileWriter<D> {
    /** The output stream we are writing to */
    private final SerializableDataOutputStream writingStream;
    /** Might not need it. Was keeping track of "original" file vs. "merge" file */
    private final boolean isMergeFile;
    /** Serializer for converting raw data to/from data items */
    private final DataItemSerializer<D> dataItemSerializer;
    /** file index number */
    private final int index;
    /**
     * The moment in time when the file should be considered as existing for.
     * When we merge files, we take the newest timestamp of the set of merge files and give it to this new file.
     */
    private final Instant creationInstant;
    /** The path to the data file we are writing */
    private final Path path;
    /** The path to the lock file for data file we are writing */
    private final Path lockFilePath;
    /**
     * Position in the file to write next. The current offset in bytes from the beginning of the file where we are
     * writing. This is very important as it used to calculate the data location pointers to the data items we have
     * written.
     */
    private long writePosition = 0;
    /** Count of the number of data items we have written so far. Ready to be stored in footer metadata */
    private long dataItemCount = 0;

    /**
     * Create a new data file in the given directory, in append mode. Puts the object into "writing" mode
     * (i.e. creates a lock file. So you'd better start writing data and be sure to finish it off).
     *
     * @param filePrefix string prefix for all files, must not contain "_" chars
     * @param dataFileDir the path to directory to create the data file in
     * @param index the index number for this file
     * @param dataItemSerializer Serializer for converting raw data to/from data items
     * @param creationTime the time stamp for the creation time for this file
     * @param isMergeFile true if this is a merge file, false if it is a new data file that has not been merged
     */
    public DataFileWriter(String filePrefix, Path dataFileDir, int index, DataItemSerializer<D> dataItemSerializer,
                          Instant creationTime, boolean isMergeFile) throws IOException {
        this.index = index;
        this.dataItemSerializer = dataItemSerializer;
        this.isMergeFile = isMergeFile;
        this.creationInstant = creationTime;
        this.path = createDataFilePath(filePrefix, dataFileDir, index, creationInstant);
        this.lockFilePath = getLockFilePath(path);
        if (Files.exists(lockFilePath)) throw new IOException("Tried to start writing to data file [" + path + "] when lock file already existed");
        writingStream = new SerializableDataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND), 4*1024*1024));
        Files.createFile(lockFilePath);
    }

    /**
     * Get the number of bytes written so far plus footer size. Tells you what the size of the file would
     * be at this moment in time if you were to close it now.
     */
    public long getFileSizeEstimate() {
        return writePosition + computePaddingLength() + FOOTER_SIZE;
    }

    /**
     * Get the path for the file being written. Useful when needing to get a reader to the file.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Write a data item copied from another file like during merge. The data item serializer copyItem() method will be
     * called to give it a chance to pass the data for or upgrade the seralization as needed.
     *
     * @param serializedVersion the serialization version the item was written with
     * @param dataItemSize The number of bytes for the data item
     * @param dataItemData ByteBuffer containing the items data
     * @return New data location in this file where it was written
     * @throws IOException If there was a problem writing the data item
     */
    public synchronized long writeCopiedDataItem(long serializedVersion, int dataItemSize, ByteBuffer dataItemData) throws IOException {
        // capture the current write position for beginning of data item
        final long byteOffset = writePosition;
        // copy the item into the file
        final int newDataItemSize = dataItemSerializer.copyItem(serializedVersion, dataItemSize, dataItemData, writingStream);
        // update writePosition
        writePosition += newDataItemSize;
        // increment data item counter
        dataItemCount++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(index, byteOffset);
    }

    /**
     * Store data item in file returning location it was stored at
     *
     * @param dataItem the data item to write
     * @return the data location of written data in bytes
     * @throws IOException If there was a problem appending data to file
     */
    public synchronized long storeDataItem(D dataItem) throws IOException {
        // find offset for the start of this new data item, we assume we always write data in a whole number of blocks
        final long byteOffset = writePosition;
        // write serialized data
        final int totalDataWritten = dataItemSerializer.serialize(dataItem,writingStream);
        // update write position
        writePosition += totalDataWritten;
        // increment data item counter
        dataItemCount++;
        // return the offset where we wrote the data
        return DataFileCommon.dataLocation(index, byteOffset);
    }

    /**
     * When you finished append to a new file, call this to seal the file and make it read only for reading.
     *
     * @param minimumValidKey The minimum valid data key at this point in time, can be used for cleaning out old data
     * @param maximumValidKey The maximum valid data key at this point in time, can be used for cleaning out old data
     * @throws IOException If there was a problem sealing file or opening again as read only
     */
    public synchronized DataFileMetadata finishWriting(long minimumValidKey, long maximumValidKey) throws IOException {
        // pad the end of file till we are a whole number of pages
        int paddingBytesNeeded = computePaddingLength();
        for (int i = 0; i < paddingBytesNeeded; i++)  writingStream.write((byte)0);
        writePosition += paddingBytesNeeded;
        // write any metadata to end of file.
        DataFileMetadata metadataFooter = new DataFileMetadata(DataFileCommon.FILE_FORMAT_VERSION,
                dataItemSerializer.getSerializedSize(),
                dataItemCount, index, creationInstant, minimumValidKey, maximumValidKey, isMergeFile,
                dataItemSerializer.getCurrentDataVersion());
        ByteBuffer footerData = metadataFooter.getFooterForWriting();
        // write footer to file
        writingStream.write(footerData.array(), footerData.position(), footerData.limit() - footerData.position());
        // close
        writingStream.flush();
        writingStream.close();
        // delete lock file
        Files.delete(lockFilePath);
        // return metadata
        return metadataFooter;
    }

    /**
     * Compute the amount of padding needed to append at the end of file to push the metadata footer so that it sits on
     * a page boundary for fast random access reading later.
     */
    private int computePaddingLength() {
        return (int)(DataFileCommon.PAGE_SIZE - (writePosition % DataFileCommon.PAGE_SIZE));
    }
}
