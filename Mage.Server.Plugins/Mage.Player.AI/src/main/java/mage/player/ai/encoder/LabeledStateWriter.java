package mage.player.ai.encoder;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5IntStorageFeatures;
import ch.systemsx.cisd.hdf5.IHDF5Writer;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Row-major HDF5 writer for MageZero-compatible datasets.
 */
public final class LabeledStateWriter implements Closeable, Flushable {

    public final Set<Integer> batchFeatures = new HashSet<>();
    public int batchStates = 0;

    private final int actionDim;
    private final IHDF5Writer writer;

    private long nRows = 0;
    private long nNnz = 0;

    public LabeledStateWriter(String path) throws IOException {
        this(path, 128, 2048, 1_000_000);
    }

    public LabeledStateWriter(String path, int actionDim, int rowsChunk, int idxChunk) throws IOException {
        this.actionDim = actionDim;
        try {
            Path parentDir = Paths.get(path).getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            this.writer = HDF5Factory.configure(path)
                    .overwrite()
                    .useUTF8CharacterEncoding()
                    .writer();

            writer.int32().createArray("/indices", 0L, idxChunk, HDF5IntStorageFeatures.INT_NO_COMPRESSION);
            writer.int64().createArray("/offsets", 1L, Math.max(rowsChunk, 512), HDF5IntStorageFeatures.INT_NO_COMPRESSION);
            writer.int64().writeArrayBlockWithOffset("/offsets", new long[]{0L}, 1, 0L);

            int rowWidth = actionDim + 4;
            writer.float32().createMatrix(
                    "/row",
                    0L,
                    rowWidth,
                    rowsChunk,
                    rowWidth,
                    HDF5FloatStorageFeatures.FLOAT_NO_COMPRESSION
            );
        } catch (Exception e) {
            throw new IOException("Failed to initialize HDF5 writer", e);
        }
    }

    public synchronized void writeRecord(LabeledState state) throws IOException {
        try {
            int[] stateVector = state.stateVector.stream().mapToInt(Integer::intValue).toArray();
            if (stateVector.length > 1) {
                Arrays.sort(stateVector);
            }
            if (stateVector.length > 0) {
                writer.int32().writeArrayBlockWithOffset("/indices", stateVector, stateVector.length, nNnz);
                nNnz += stateVector.length;
            }
            writer.int64().writeArrayBlockWithOffset("/offsets", new long[]{nNnz}, 1, nRows + 1);

            int rowWidth = actionDim + 4;
            float[] row = new float[rowWidth];

            int copy = Math.min(actionDim, state.actionVector.length);
            for (int i = 0; i < copy; i++) {
                row[i] = (float) state.actionVector[i];
            }
            for (int i = copy; i < actionDim; i++) {
                row[i] = 0f;
            }

            row[actionDim] = (float) state.resultLabel;
            row[actionDim + 1] = (float) state.stateScore;
            row[actionDim + 2] = state.isPlayer ? 1f : 0f;
            row[actionDim + 3] = (float) state.actionType.ordinal();

            writer.float32().writeMatrixBlockWithOffset("/row", new float[][]{row}, 1, rowWidth, nRows, 0);

            nRows++;
            batchStates++;
            batchFeatures.addAll(state.stateVector);
        } catch (Exception e) {
            throw new IOException("HDF5 append failed", e);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        try {
            writer.file().flush();
        } catch (Exception e) {
            throw new IOException("HDF5 flush failed", e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            flush();
        } catch (IOException ignored) {
        }
        try {
            writer.close();
        } catch (Exception ignored) {
        }
    }
}
