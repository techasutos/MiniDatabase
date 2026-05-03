package com.minidb.storage.disk;

import java.io.IOException;

/**
 * DiskManager is responsible for managing disk I/O operations for the database.
 * It provides methods to read and write pages to disk.
 * In a real implementation, this would handle page caching, buffering, and efficient disk access.
 * For simplicity, this interface abstracts away those details and focuses on the core read/write operations.
 * In a production system, you would likely have additional methods for managing free pages, handling transactions, and ensuring durability.
 * This interface is designed to be implemented by a class that interacts with the underlying file system to store and retrieve data pages.
 * The pageId parameter is an identifier for the page being read or written, and the data parameter is the byte array representing the contents of the page.
 * The methods throw IOException to indicate potential issues with disk access, such as file not found, permission issues, or hardware failures.
 * Overall, the DiskManager is a crucial component of the storage layer in a database system, enabling efficient and reliable access to data stored on disk.
 * In a complete implementation, you would also need to consider aspects such as page size, page layout, and how to handle concurrent access to the disk.
 * This interface serves as a foundational contract for any class that aims to manage disk storage for the database, ensuring that it provides the necessary
 * functionality to read and write data pages effectively.
 */
public interface DiskManager {
    void writePage(int pageId, byte[] data) throws IOException;
    byte[] readPage(int pageId) throws IOException;
}