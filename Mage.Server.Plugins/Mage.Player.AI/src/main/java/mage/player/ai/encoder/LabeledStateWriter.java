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
<<<<<<< HEAD
 * Row-major HDF5 writer for MageZero datasets.
 *
 * Layout:
 *   /indices : int32 [NNZ]             (CSR values)
 *   /offsets : int64 [N+1]             (CSR row offsets; offsets[0]==0)
 *   /row     : float32 [N, A+4]        (row-major: action(A), resultLabel, stateScore, isPlayer, actionType)
 *
 * Notes:
 * - No compression (fastest random read).
 * - Chunk rows by rowsChunk; columns are fixed width (A+4).
 * - Indices are sorted per row to keep CSR tidy (optional).
=======
 * Row-major HDF5 writer for MageZero-compatible datasets.
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
 */
public final class LabeledStateWriter implements Closeable, Flushable {

    public final Set<Integer> batchFeatures = new HashSet<>();
    public int batchStates = 0;

<<<<<<< HEAD
    private final int actionDim;   // A

    private final IHDF5Writer writer;

    private long nRows = 0; // N
    private long nNnz  = 0; // total entries in /indices

    public LabeledStateWriter(String path) throws IOException {
        this(path, /*actionDim*/128, /*rowsChunk*/2048, /*idxChunk*/1_000_000);
=======
    private final int actionDim;
    private final IHDF5Writer writer;

    private long nRows = 0;
    private long nNnz = 0;

    public LabeledStateWriter(String path) throws IOException {
        this(path, 128, 2048, 1_000_000);
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
    }

    public LabeledStateWriter(String path, int actionDim, int rowsChunk, int idxChunk) throws IOException {
        this.actionDim = actionDim;
<<<<<<< HEAD
        // chunk size for rows
        // chunk size for /indices

        try {

=======
        try {
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
            Path parentDir = Paths.get(path).getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
<<<<<<< HEAD
            
=======

>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
            this.writer = HDF5Factory.configure(path)
                    .overwrite()
                    .useUTF8CharacterEncoding()
                    .writer();

<<<<<<< HEAD
            // indices: 1D int32, extendable, uncompressed
            writer.int32().createArray(
                    "/indices",
                    /*initialSize*/0L,
                    /*blockSize*/idxChunk,
                    HDF5IntStorageFeatures.INT_NO_COMPRESSION
            );

            // offsets: 1D int64, extendable, uncompressed; seed with one 0
            writer.int64().createArray(
                    "/offsets",
                    /*initialSize*/1L,
                    /*blockSize*/Math.max(rowsChunk, 512),
                    HDF5IntStorageFeatures.INT_NO_COMPRESSION
            );
            writer.int64().writeArrayBlockWithOffset("/offsets", new long[]{0L}, 1, 0L);

            // row: 2D float32 [N, actionDim+4], extendable rows, row-major chunks, uncompressed
            int rowWidth = actionDim + 4;
            writer.float32().createMatrix(
                    "/row",
                    /*sizeX rows*/0L,
                    /*sizeY cols*/rowWidth,
                    /*blockX*/rowsChunk,
                    /*blockY*/rowWidth,
=======
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
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
                    HDF5FloatStorageFeatures.FLOAT_NO_COMPRESSION
            );
        } catch (Exception e) {
            throw new IOException("Failed to initialize HDF5 writer", e);
        }
    }

<<<<<<< HEAD
    /** Append one record. */
    public synchronized void writeRecord(LabeledState s) throws IOException {
        try {
            // --- CSR append for stateVector ---
            int[] sv = s.stateVector.stream().mapToInt(Integer::intValue).toArray();
            if (sv.length > 1) Arrays.sort(sv);
            if (sv.length > 0) {
                writer.int32().writeArrayBlockWithOffset("/indices", sv, sv.length, nNnz);
                nNnz += sv.length;
            }
            writer.int64().writeArrayBlockWithOffset("/offsets", new long[]{ nNnz }, 1, nRows + 1);

            // --- pack fixed row as float32 ---
            final int W = actionDim + 4;
            float[] row = new float[W];

            // actions (truncate/pad)
            int copy = Math.min(actionDim, s.actionVector.length);
            for (int i = 0; i < copy; i++) row[i] = (float) s.actionVector[i];
            for (int i = copy; i < actionDim; i++) row[i] = 0f;

            // scalars
            row[actionDim] = (float) s.resultLabel;         // resultLabel
            row[actionDim + 1] = (float) s.stateScore;          // stateScore
            row[actionDim + 2] = s.isPlayer ? 1f : 0f;          // isPlayer
            row[actionDim + 3] = (float) s.actionType.ordinal();// actionType as float

            // write one row
            writer.float32().writeMatrixBlockWithOffset(
                    "/row",
                    new float[][]{ row },
                    /*blockSizeX*/1,
                    /*blockSizeY*/W,
                    /*offsetX*/nRows,
                    /*offsetY*/0
            );

            nRows++;
            batchStates++;
            batchFeatures.addAll(s.stateVector);
=======
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
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
        } catch (Exception e) {
            throw new IOException("HDF5 append failed", e);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
<<<<<<< HEAD
        try { writer.file().flush(); }
        catch (Exception e) { throw new IOException("HDF5 flush failed", e); }
=======
        try {
            writer.file().flush();
        } catch (Exception e) {
            throw new IOException("HDF5 flush failed", e);
        }
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
    }

    @Override
    public synchronized void close() throws IOException {
<<<<<<< HEAD
        try { flush(); } catch (IOException ignored) {}
        try { writer.close(); } catch (Exception ignored) {}
=======
        try {
            flush();
        } catch (IOException ignored) {
        }
        try {
            writer.close();
        } catch (Exception ignored) {
        }
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
    }
}
