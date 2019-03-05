package com.diegofdez.tools.imageedit.service;

import com.diegofdez.tools.imageedit.api.dto.FileRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ImageEditService {
    private static final String EXIF_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss";

    public void getImageMetadataFromFile(FileRequest request){
        InputStream image = getInputStreamFromFile(request.getFile());
        Date date = getImageDate(image, request.getFile().getOriginalFilename());
        log.info("Image {} from date {}", request.getFile().getOriginalFilename(), date.toString());
    }

    public Date getImageDate(InputStream image, String imageName) {
        JpegImageMetadata imageMetadata = getImageMetadata(image, imageName);
        return getImageDate(imageMetadata);
    }

    private JpegImageMetadata getImageMetadata(InputStream image, String imageName) {
        try {
            return (JpegImageMetadata) Imaging.getMetadata(image, imageName);
        } catch (ImageReadException | IOException e) {
            log.error("Couldn't get metadata for image {}", imageName);
            log.error("Stacktrace: ", e);
            return null;
        }
    }

    public Date getImageDate(JpegImageMetadata imageMetadata) {
        // Get Date in String Format
        String dateString = imageMetadata.findEXIFValueWithExactMatch(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL).getValueDescription();

        // Process date to return in Date format
        // Remove single quotes in string
        dateString = dateString.substring(1, dateString.length() - 1);
        // Build date element from string element
        SimpleDateFormat dateFormat = new SimpleDateFormat(ImageEditService.EXIF_DATE_FORMAT);

        try {
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            log.error("Couldn't get image date");
            return null;
        }
    }


    public Date getImageDate(File image) {
        JpegImageMetadata imageMetadata = getImageMetadata(image);
        return getImageDate(imageMetadata);
    }

    private InputStream getInputStreamFromFile(MultipartFile multipartFile) {
        try {
            InputStream newFile = new BufferedInputStream(multipartFile.getInputStream());
            log.info("Multipart {} transferred to file", multipartFile.getOriginalFilename());
            return newFile;
        } catch (IOException e) {
            log.error("Couldn't get file from {}", multipartFile.getOriginalFilename());
            return null;
        }
    }

    private JpegImageMetadata getImageMetadata(File image) {
        try {
            return (JpegImageMetadata) Imaging.getMetadata(image);
        } catch (ImageReadException | IOException e) {
            log.error("Couldn't get metadata for image {}", image.getName());
            log.error("Stacktrace: ", e);
            return null;
        }
    }

    public Map<String, String> getImageMetadataMap(File image) {
        Map<String, String> result = new HashMap<>();
        JpegImageMetadata imageMetadata = getImageMetadata(image);
        for (ImageMetadata.ImageMetadataItem metadataItem : imageMetadata.getItems()) {
            log.info("Item: {}", metadataItem.toString());
        }
        return result;
    }

    public void shiftImageDate(File image, int days, int hours, int minutes, int seconds)
            throws ParseException, ImageReadException, IOException, ImageWriteException {

        JpegImageMetadata imageMetadata = getImageMetadata(image);

        if (imageMetadata != null) {
            Date imageDate = getImageDate(imageMetadata);
            log.info("BEFORE: {}", imageDate);

            Calendar cal = Calendar.getInstance();
            cal.setTime(imageDate);
            cal.add(Calendar.DAY_OF_MONTH, days);
            cal.add(Calendar.HOUR, hours);
            cal.add(Calendar.MINUTE, minutes);
            cal.add(Calendar.SECOND, seconds);
            imageDate = cal.getTime();

            SimpleDateFormat dateFormat = new SimpleDateFormat(ImageEditService.EXIF_DATE_FORMAT);
            String imageDateString = dateFormat.format(imageDate);
            log.info("AFTER: {}", imageDate);

            TiffImageMetadata exif = imageMetadata.getExif();
            if (exif != null) {
                TiffOutputSet outputSet = exif.getOutputSet();

                if (outputSet != null) {
                    // Remove and add field to set new value
                    TiffOutputDirectory outputDirectory = outputSet.getOrCreateExifDirectory();
                    outputDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                    outputDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, new String[]{imageDateString});

                    // Create temporal image with desired metadata
                    File temporalImage = new File(image.getPath() + ".tmp");
                    FileOutputStream fos = new FileOutputStream(temporalImage);
                    OutputStream os = new BufferedOutputStream(fos);
                    new ExifRewriter().updateExifMetadataLossless(image, os, outputSet);

                    // Remove old image
                    image.delete();
                    // Rename temporal image to original name
                    temporalImage.renameTo(image);
                }
            }
        }
    }


    public void shiftImageDateBatch(List<File> imageList, int days, int hours, int minutes, int seconds, boolean ignoreException)
            throws ImageWriteException, IOException, ParseException, ImageReadException {
        Iterator<File> imageListIterator = imageList.iterator();
        while (imageListIterator.hasNext()) {
            File image = imageListIterator.next();
            System.out.println(image.getName());
            try {
                shiftImageDate(image, days, hours, minutes, seconds);
            } catch (ImageReadException ex) {
                if (!ignoreException) {
                    throw ex;
                }
                System.out.println("Couldn't process " + image.getPath());
                System.out.println("   " + ex.getMessage());
            } catch (ParseException ex) {
                if (!ignoreException) {
                    throw ex;
                }
                System.out.println("Couldn't process " + image.getPath());
                System.out.println("   " + ex.getMessage());
            }
        }
    }

    public void shiftImageDateFolder(File folder, int days, int hours, int minutes, int seconds, boolean ignoreException)
            throws IOException, ParseException, ImageWriteException, ImageReadException {
        if (folder.isDirectory()) {
            List<File> imageList = Files.walk(Paths.get(folder.getPath()))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            shiftImageDateBatch(imageList, days, hours, minutes, seconds, ignoreException);
        }
    }
}
