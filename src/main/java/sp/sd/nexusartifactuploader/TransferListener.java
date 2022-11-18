package sp.sd.nexusartifactuploader;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import hudson.model.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferResource;
import org.apache.commons.lang3.Validate;

public class TransferListener extends AbstractTransferListener {
    Logger logger = LoggerFactory.getLogger(TransferListener.class);
    private ConcurrentMap<TransferResource, Long> downloads = new ConcurrentHashMap<>();
    private int lastLength;
    private TaskListener Listener;

    static class FileSizeFormat {
        enum ScaleUnit {
            BYTE {
                @Override
                public long bytes() {
                    return 1L;
                }

                @Override
                public String symbol() {
                    return "B";
                }
            },
            KILOBYTE {
                @Override
                public long bytes() {
                    return 1000L;
                }

                @Override
                public String symbol() {
                    return "kB";
                }
            },
            MEGABYTE {
                @Override
                public long bytes() {
                    return KILOBYTE.bytes() * KILOBYTE.bytes();
                }

                @Override
                public String symbol() {
                    return "MB";
                }
            },
            GIGABYTE {
                @Override
                public long bytes() {
                    return MEGABYTE.bytes() * KILOBYTE.bytes();
                }

                @Override
                public String symbol() {
                    return "GB";
                }
            };

            public abstract long bytes();

            public abstract String symbol();

            public static ScaleUnit getScaleUnit(long size) {
                Validate.isTrue(size >= 0, "File size cannot be negative: %s", size);

                if (size >= GIGABYTE.bytes()) {
                    return GIGABYTE;
                } else if (size >= MEGABYTE.bytes()) {
                    return MEGABYTE;
                } else if (size >= KILOBYTE.bytes()) {
                    return KILOBYTE;
                } else {
                    return BYTE;
                }
            }
        }

        private DecimalFormat smallFormat;
        private DecimalFormat largeFormat;

        public FileSizeFormat(Locale locale) {
            smallFormat = new DecimalFormat("#0.0", new DecimalFormatSymbols(locale));
            largeFormat = new DecimalFormat("###0", new DecimalFormatSymbols(locale));
        }

        public String format(long size) {
            return format(size, null);
        }

        public String format(long size, ScaleUnit unit) {
            return format(size, unit, false);
        }

        public String format(long size, ScaleUnit unit, boolean omitSymbol) {
            Validate.isTrue(size >= 0, "File size cannot be negative: %s", size);

            if (unit == null) {
                unit = ScaleUnit.getScaleUnit(size);
            }

            double scaledSize = (double) size / unit.bytes();
            String scaledSymbol = " " + unit.symbol();

            if (omitSymbol) {
                scaledSymbol = "";
            }

            if (unit == ScaleUnit.BYTE) {
                return largeFormat.format(size) + scaledSymbol;
            }

            if (scaledSize < 0.05 || scaledSize >= 10.0) {
                return largeFormat.format(scaledSize) + scaledSymbol;
            } else {
                return smallFormat.format(scaledSize) + scaledSymbol;
            }
        }

        public String formatProgress(long progressedSize, long size) {
            Validate.isTrue(progressedSize >= 0L, "Progressed file size cannot be negative: %s", progressedSize);
            Validate.isTrue(size < 0L || progressedSize <= size,
                    "Progressed file size cannot be bigger than size: %s > %s", progressedSize, size);

            if (size >= 0 && progressedSize != size) {
                ScaleUnit unit = ScaleUnit.getScaleUnit(size);
                String formattedProgressedSize = format(progressedSize, unit, true);
                String formattedSize = format(size, unit);

                return formattedProgressedSize + "/" + formattedSize;
            } else {
                return format(progressedSize);
            }
        }
    }

    public TransferListener(TaskListener Listener) {
        this.Listener = Listener;
    }

    @Override
    public void transferInitiated(TransferEvent event) {
        String type = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";

        TransferResource resource = event.getResource();
        Listener.getLogger().println(type + ": " + resource.getRepositoryUrl() + resource.getResourceName());
    }

    @Override
    public void transferCorrupted(TransferEvent event)
            throws TransferCancelledException {
        TransferResource resource = event.getResource();
        Listener.getLogger().println("[WARNING] " + event.getException().getMessage() + " for " + resource.getRepositoryUrl()
                + resource.getResourceName());
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        TransferResource resource = event.getResource();
        long contentLength = event.getTransferredBytes();

        FileSizeFormat format = new FileSizeFormat(Locale.ENGLISH);
        String type = (event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded");
        String len = format.format(contentLength);

        String throughput = "";
        long duration = System.currentTimeMillis() - resource.getTransferStartTime();
        if (duration > 0L) {
            double bytesPerSecond = contentLength / (duration / 1000.0);
            throughput = " at " + format.format((long) bytesPerSecond) + "/s";
        }
        Listener.getLogger().println(type + ": " + resource.getRepositoryUrl() + resource.getResourceName() + " (" + len
                + throughput + ")");
    }

    int lastPercentage = 0;

    public void transferProgressed(TransferEvent event) throws TransferCancelledException {
        TransferResource resource = event.getResource();
        downloads.put(resource, Long.valueOf(event.getTransferredBytes()));

        StringBuilder buffer = new StringBuilder();

        for (Map.Entry<TransferResource, Long> entry : downloads.entrySet()) {
            long total = entry.getKey().getContentLength();
            long complete = entry.getValue().longValue();
            long percentageComplete = (complete * 100) / total;
            if (percentageComplete >= lastPercentage + 10) {
                buffer.append(percentageComplete).append(" ");
                lastPercentage = (int) percentageComplete;
                Listener.getLogger().println(buffer + "% completed (" + getStatus(complete, total) + ").");
            }
        }
    }

    private String getStatus(long complete, long total) {
        FileSizeFormat format = new FileSizeFormat(Locale.ENGLISH);

        if (total >= 1024) {
            return format.format(complete) + " / " + format.format(total);
        } else if (total >= 0) {
            return format.format(complete) + " / " + format.format(total);
        } else if (complete >= 1024) {
            return format.format(complete);
        } else {
            return format.format(complete);
        }
    }
}
