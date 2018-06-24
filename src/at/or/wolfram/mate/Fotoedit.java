
package at.or.wolfram.mate.fotoedit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.AllTagConstants;
import org.apache.sanselan.formats.tiff.constants.ExifTagConstants;
import org.apache.sanselan.formats.tiff.constants.TiffDirectoryConstants;
import org.apache.sanselan.formats.tiff.fieldtypes.FieldType;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputField;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;

import com.drew.imaging.ImageProcessingException;

public class Main {

	private static final String JPEG_EXTENSION = ".jpg";
	private static final String CAPITAL_JPEG_EXTENSION = ".JPG";
	private static final int DATE_LENGTH = 22;
	private static final String NO_DATE = "1970:01:01 00:00:00";
	private static final String DELIMITER = ";";
	
	private static final String COMMAND_WRITE_INFO = "write";
	private static final String COMMAND_APPLY_INFO = "apply";

	/**
	 * @param args
	 * @throws IOException 
	 * @throws ImageProcessingException 
	 * @throws ImageWriteException 
	 * @throws ImageReadException 
	 */
	public static void main(String[] args) throws ImageProcessingException, IOException, ImageReadException, ImageWriteException {
		if (args.length != 3) {
			System.out.println("Please specify source folder");
			return;
		}

		String folderName = args[0];
		String infoFileName = args[1];
		String commandName = args[2];
		Main main = new Main();

		if (COMMAND_WRITE_INFO.equals(commandName)) {
			main.writeFileNamesAndDates(folderName, infoFileName);
		}
		else if (COMMAND_APPLY_INFO.equals(commandName)) {
			main.applyDatesToFiles(folderName, infoFileName);
		}
		else {
			System.out.println("Please choose a valid command. Choices: ");
			System.out.println(COMMAND_WRITE_INFO + ", " + COMMAND_APPLY_INFO);
		}
	}
	
	private void applyDatesToFiles(String directoryStr, String infoFilePath) throws ImageReadException, ImageWriteException, IOException {
		Map<String, String> filesAndDates = readFileNamesAndDates(infoFilePath);
		
		Collection<File> files = listJPEG(directoryStr);
		for (File f : files) {
			if (filesAndDates.containsKey(f.getName())) {
				String fileNamePrefix = filesAndDates.get(f.getName()).replace(":", "-").trim() + " - ";
				String targetFilePath = f.getParent() + "\\" + fileNamePrefix + f.getName();
				System.out.println("Target: [" + targetFilePath + "]");
				System.out.println("Changing date of file " + f.getAbsolutePath() + " to " + filesAndDates.get(f.getName()));
				writeDateTaken(f, new File(targetFilePath), filesAndDates.get(f.getName()));
			}
		}
	}
	
	private Map<String, String> readFileNamesAndDates(String infoFilePath) throws IOException {
		Map<String, String> fileNamesAndDates = new HashMap<String, String>();
		File infoFile = new File(infoFilePath);
		List<String> lines = FileUtils.readLines(infoFile);
		for (String line : lines) {
			if (line != null && line.length() > 0) {
				String[] parts = line.split(DELIMITER);
				if (parts == null || parts.length != 2) {
					System.err.println("Invalid info line: " + line);
					continue;
				}
				String name = parts[0];
				String date = parts[1];
				fileNamesAndDates.put(name, date);
			}
		}
		return fileNamesAndDates;
	}
	
	private void writeFileNamesAndDates(String directoryStr, String infoFilePath) throws IOException, ImageReadException {
		File infoFile = new File(infoFilePath);
		
		Collection<File> files = listJPEG(directoryStr);
		for (File f : files) {
			String line = getOriginalFileName(f.getName()) + DELIMITER + getDateTaken(f) + System.getProperty("line.separator");
			FileUtils.writeStringToFile(infoFile, line, true);
		}
	}
	
	private Collection<File> listJPEG(String directoryStr) {
		List<String> extensions = new ArrayList<String>();
		extensions.add(JPEG_EXTENSION);
		extensions.add(CAPITAL_JPEG_EXTENSION);

		File directory = new File(directoryStr);
		Collection<File> fileCollection = FileUtils.listFiles(directory, new SuffixFileFilter(extensions), TrueFileFilter.INSTANCE);

		return fileCollection;
	}

	private String getOriginalFileName(String fileNameWithDate) {
		if (fileNameWithDate.length() > DATE_LENGTH) {
			return fileNameWithDate.substring(DATE_LENGTH);
		}
		return fileNameWithDate;
	}

	private String getDateTaken(File jpeg) throws ImageReadException, IOException {
		JpegImageMetadata metadata = ((JpegImageMetadata)Sanselan.getMetadata(jpeg));
		final TiffImageMetadata exif = metadata.getExif();
		
		for (Object o : exif.getAllFields()) {
			TiffField tf = (TiffField)o;
			if (tf.tagInfo == ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL) {
				return tf.getStringValue();
			}
		}
		
		return NO_DATE;
	}

	private void writeDateTaken(File jpeg, File targetJpeg, String dateTaken) throws ImageReadException, IOException, ImageWriteException {
		JpegImageMetadata metadata = ((JpegImageMetadata)Sanselan.getMetadata(jpeg));
		final TiffImageMetadata exif = metadata.getExif();
		String dateTimeTagNewContents = dateTaken;
		TiffOutputSet outputSet = exif.getOutputSet();
		TiffOutputDirectory exifDir = outputSet.findDirectory(TiffDirectoryConstants.DIRECTORY_TYPE_EXIF);
		exifDir.removeField(AllTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
		TiffOutputField dateTimeOriginalNew = new TiffOutputField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, FieldType.FIELD_TYPE_ASCII, dateTimeTagNewContents.length(), dateTimeTagNewContents.getBytes());
		exifDir.add(dateTimeOriginalNew);
		new ExifRewriter().updateExifMetadataLossless(jpeg, new FileOutputStream(targetJpeg), outputSet);
	}

}
