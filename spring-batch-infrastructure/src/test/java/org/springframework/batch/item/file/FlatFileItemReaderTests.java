package org.springframework.batch.item.file;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.item.file.separator.RecordSeparatorPolicy;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

/**
 * Tests for {@link FlatFileItemReader}.
 */
public class FlatFileItemReaderTests {

	// common value used for writing to a file
	private String TEST_STRING = "FlatFileInputTemplate-TestData";

	private FlatFileItemReader<String> reader = new FlatFileItemReader<String>();

	private ExecutionContext executionContext = new ExecutionContext();

	@Before
	public void setUp() {

		reader.setResource(getInputResource("testLine1\ntestLine2\ntestLine3\ntestLine4\ntestLine5\ntestLine6"));
		reader.setLineMapper(new PassThroughLineMapper());
	}

	@Test
	public void testRestartWithCustomRecordSeparatorPolicy() throws Exception {

		reader.setRecordSeparatorPolicy(new RecordSeparatorPolicy() {
			// 1 record = 2 lines
			boolean pair = true;

			public boolean isEndOfRecord(String line) {
				pair = !pair;
				return pair;
			}

			public String postProcess(String record) {
				return record;
			}

			public String preProcess(String record) {
				return record;
			}
		});

		reader.open(executionContext);

		assertEquals("testLine1testLine2", reader.read());
		assertEquals("testLine3testLine4", reader.read());

		reader.update(executionContext);

		reader.close();

		reader.open(executionContext);

		assertEquals("testLine5testLine6", reader.read());
	}

	@Test
	public void testRestartWithSkippedLines() throws Exception {

		reader.setLinesToSkip(2);
		reader.open(executionContext);

		// read some records
		reader.read();
		reader.read();
		// get restart data
		reader.update(executionContext);
		// read next two records
		reader.read();
		reader.read();

		assertEquals(2, executionContext.getInt(ClassUtils.getShortName(FlatFileItemReader.class) + ".read.count"));
		// close input
		reader.close();

		reader
				.setResource(getInputResource("header\nignoreme\ntestLine1\ntestLine2\ntestLine3\ntestLine4\ntestLine5\ntestLine6"));

		// init for restart
		reader.open(executionContext);

		// read remaining records
		assertEquals("testLine3", reader.read());
		assertEquals("testLine4", reader.read());

		reader.update(executionContext);
		assertEquals(4, executionContext.getInt(ClassUtils.getShortName(FlatFileItemReader.class) + ".read.count"));
	}

	@Test
	public void testCurrentItemCount() throws Exception {

		reader.setCurrentItemCount(2);
		reader.open(executionContext);

		// read some records
		reader.read();
		reader.read();
		// get restart data
		reader.update(executionContext);

		assertEquals(4, executionContext.getInt(ClassUtils.getShortName(FlatFileItemReader.class) + ".read.count"));
		// close input
		reader.close();

	}

	@Test
	public void testMaxItemCount() throws Exception {

		reader.setMaxItemCount(2);
		reader.open(executionContext);

		// read some records
		reader.read();
		reader.read();
		// get restart data
		reader.update(executionContext);
		assertNull(reader.read());

		assertEquals(2, executionContext.getInt(ClassUtils.getShortName(FlatFileItemReader.class) + ".read.count"));
		// close input
		reader.close();

	}

	@Test
	public void testMaxItemCountFromContext() throws Exception {

		reader.setMaxItemCount(2);
		executionContext.putInt(reader.getClass().getSimpleName()+".read.count.max", Integer.MAX_VALUE);
		reader.open(executionContext);
		// read some records
		reader.read();
		reader.read();
		assertNotNull(reader.read());
		// close input
		reader.close();

	}

	@Test
	public void testCurrentItemCountFromContext() throws Exception {

		reader.setCurrentItemCount(2);
		executionContext.putInt(reader.getClass().getSimpleName()+".read.count", 3);
		reader.open(executionContext);
		// read some records
		assertEquals("testLine4", reader.read());
		// close input
		reader.close();

	}

	@Test
	public void testMaxAndCurrentItemCount() throws Exception {

		reader.setMaxItemCount(2);
		reader.setCurrentItemCount(2);
		reader.open(executionContext);
		// read some records
		assertNull(reader.read());
		// close input
		reader.close();

	}

	@Test
	public void testNonExistentResource() throws Exception {

		Resource resource = new NonExistentResource();

		reader.setResource(resource);

		// afterPropertiesSet should only throw an exception if the Resource is
		// null
		reader.afterPropertiesSet();

		reader.setStrict(false);
		reader.open(executionContext);
		assertNull(reader.read());
		reader.close();
	}

	@Test
	public void testRuntimeFileCreation() throws Exception {

		Resource resource = new NonExistentResource();

		reader.setResource(resource);

		// afterPropertiesSet should only throw an exception if the Resource is
		// null
		reader.afterPropertiesSet();

		// replace the resource to simulate runtime resource creation
		reader.setResource(getInputResource(TEST_STRING));
		reader.open(executionContext);
		assertEquals(TEST_STRING, reader.read());
	}

	/**
	 * In strict mode, resource must exist at the time reader is opened.
	 */
	@Test(expected = ItemStreamException.class)
	public void testStrictness() throws Exception {

		Resource resource = new NonExistentResource();

		reader.setResource(resource);
		reader.setStrict(true);

		reader.afterPropertiesSet();

		reader.open(executionContext);
	}

	private Resource getInputResource(String input) {
		return new ByteArrayResource(input.getBytes());
	}

	private static class NonExistentResource extends AbstractResource {

		public NonExistentResource() {
		}

		public boolean exists() {
			return false;
		}

		public String getDescription() {
			return "NonExistentResource";
		}

		public InputStream getInputStream() throws IOException {
			return null;
		}
	}
}
