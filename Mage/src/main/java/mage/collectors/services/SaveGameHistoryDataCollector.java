package mage.collectors.services;

import mage.cards.decks.DeckFormats;
import mage.constants.CardType;
import mage.constants.TableState;
import mage.game.Game;
import mage.MageObject;
import mage.game.Table;
import mage.game.match.MatchPlayer;
import mage.players.Player;
import mage.util.CardUtil;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Data collector that can collect and save whole games history and store it in disk system.
 * <p>
 * WARNING, it's not production ready yet, use for load tests only (see todos below)
 * <p>
 * Possible use cases:
 * - load tests debugging to find freeze AI games;
 * - public server debugging to find human freeze games;
 * - AI learning data collection;
 * - fast access to saved decks for public tourneys, e.g. after draft
 * Tasks:
 * - TODO: drafts - save picks history per player;
 * - TODO: tourneys - fix chat logs
 * - TODO: tourneys - fix miss end events on table or server quite (active table/game freeze bug)
 * <p>
 * Data structure example:
 * - gamesHistory
 * -   2025-07-04
 * -     tables_active
 * -     tables_done
 * -       table 1 - UUID
 * -         table_logs.txt
 * -           games_done
 * -           games_active
 * -             game 1 - UUID
 * -             game 2 - UUID
 * -               game_logs.html
 * -               chat_logs.html
 * -               deck_player_1.dck
 * -               deck_player_2.dck
 *
 * @author JayDi85
 */
public class SaveGameHistoryDataCollector extends EmptyDataCollector {

    private static final Logger logger = Logger.getLogger(SaveGameHistoryDataCollector.class);
    public static final String SERVICE_CODE = "saveGameHistory";

    private static final String DIR_NAME_ROOT = "gamesHistory";
    private static final SimpleDateFormat DIR_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final String DIR_NAME_TABLES_ACTIVE = "tables_active";
    private static final String DIR_NAME_TABLES_DONE = "tables_done";
    private static final String DIR_NAME_GAMES_ACTIVE = "games_active";
    private static final String DIR_NAME_GAMES_DONE = "games_done";

    private static final String TABLE_LOGS_FILE_NAME = "table_logs.txt";
    private static final String TABLE_CHAT_FILE_NAME = "table_chat.txt";
    private static final String DECK_FILE_NAME_FORMAT = "deck_player_%d.dck";
    private static final String GAME_LOGS_FILE_NAME = "game_logs.html";
    private static final String GAME_CHAT_FILE_NAME = "game_chat.txt";

    private static final UUID NO_TABLE_ID = UUID.randomUUID();
    private static final String NO_TABLE_NAME = "SINGLE"; // need for unit tests

    // global switch
    boolean enabled;

    // prepared dirs for each table or game - if it returns empty string then logs will be disabled, e.g. on too many data
    Map<UUID, String> tableDirs = new ConcurrentHashMap<>();
    Map<UUID, String> gameDirs = new ConcurrentHashMap<>();

    // stats: gameId -> playerName -> cardType -> count
    Map<UUID, Map<String, Map<CardType, Integer>>> gameStats = new ConcurrentHashMap<>();

    // all write operations must be done in single thread
    // TODO: analyse load tests performance and split locks per table/game
    // TODO: limit file sizes for possible game freeze?
    ReentrantLock writeLock = new ReentrantLock();

    public SaveGameHistoryDataCollector() {
        // prepare
        Path root = Paths.get(DIR_NAME_ROOT);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                logger.error("Can't create root dir, games history data collector will be disabled - " + e, e);
                this.enabled = false;
                return;
            }
        }

        this.enabled = true;
    }

    @Override
    public String getServiceCode() {
        return SERVICE_CODE;
    }

    @Override
    public String getInitInfo() {
        return "save all game history to " + Paths.get(DIR_NAME_ROOT, DIR_DATE_FORMAT.format(new Date())).toAbsolutePath();
    }

    @Override
    public void onTableStart(Table table) {
        if (!this.enabled) return;
        writeToTableLogsFile(table, new Date() + " [START] " + table.getId() + ", " + table);
    }

    @Override
    public void onTableEnd(Table table) {
        if (!this.enabled) return;
        writeToTableLogsFile(table, new Date() + " [END] " + table.getId() + ", " + table);

        // good end - move all files to done folder and change dir refs for possible game and other logs
        writeLock.lock();
        try {
            String oldFolder = getOrCreateTableDir(table.getId(), table.getParentTableId(), table.getTableIndex(), false);
            if (oldFolder.contains(DIR_NAME_TABLES_ACTIVE)) {
                // move files
                String newFolder = oldFolder.replace(DIR_NAME_TABLES_ACTIVE, DIR_NAME_TABLES_DONE);
                Files.createDirectories(Paths.get(newFolder));
                Files.move(Paths.get(oldFolder), Paths.get(newFolder), StandardCopyOption.REPLACE_EXISTING);

                // update all refs (table and games)
                this.tableDirs.put(table.getId(), newFolder);
                this.gameDirs.replaceAll((gameId, gameFolder) ->
                        gameFolder.startsWith(oldFolder)
                                ? gameFolder.replace(oldFolder, newFolder)
                                : gameFolder
                );
                innerDirDeleteEmptyParent(oldFolder);
            }
        } catch (IOException e) {
            logger.error("Can't move table files to done folder: " + e, e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void onGameStart(Game game) {
        if (!this.enabled) return;
        writeToGameLogsFile(game, new Date() + " [START] " + game.getId() + ", " + game);

        // save deck files
        writeLock.lock();
        try {
            String gameDir = getOrCreateGameDir(game, isActive(game));
            if (gameDir.isEmpty()) {
                return;
            }
            int playerNum = 0;
            for (Player player : game.getPlayers().values()) {
                playerNum++;
                MatchPlayer matchPlayer = player.getMatchPlayer();
                if (matchPlayer != null && matchPlayer.getDeck() != null) {
                    String deckFile = Paths.get(gameDir, String.format(DECK_FILE_NAME_FORMAT, playerNum)).toString();
                    DeckFormats.XMAGE.getExporter().writeDeck(deckFile, matchPlayer.getDeckForViewer().prepareCardsOnlyDeck());
                }
            }
        } catch (IOException e) {
            logger.error("Can't write deck file for game " + game.getId() + ": " + e, e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void onGameLog(Game game, String message) {
        if (!this.enabled) return;
        writeToGameLogsFile(game, new Date() + " [LOG] " + CardUtil.getTurnInfo(game) + ": " + message);

        // Track when a player casts a spell or plays a land by monitoring the log messages.
        // We use regex to match the standardized log formats. Crucially, we match 'from hand' to 
        // capture both spells being cast AND lands being put onto the battlefield, while avoiding 
        // double-counting permanents resolving ('from stack onto the Battlefield').
        // Format: <font color='#20B2AA'>PlayerName</font> [casts/puts] <font color='...' object_id='...'>CardName</font> [hex] from hand
        Pattern pattern = Pattern.compile("<font color='#20B2AA'>(.*?)</font> (?:casts|puts) <font color='.*?' object_id='(.*?)'>(.*?)</font> \\[[a-f0-9]+\\] from hand");
        Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            String playerName = matcher.group(1);
            String objectIdStr = matcher.group(2);
            
            try {
                UUID objectId = UUID.fromString(objectIdStr);
                // Retrieve the actual MageObject using its parsed UUID.
                // This allows us to access the actual game state data for the card, rather than relying on the text name.
                MageObject mageObject = game.getObject(objectId);
                if (mageObject != null) {
                    recordTypeStats(game.getId(), playerName, mageObject, game);
                }
            } catch (IllegalArgumentException e) {
                // Safely ignore UUID parsing errors if a log string somehow triggered a false positive match
            }
        }
    }

    /**
     * Examines a played/cast card's types and updates the player's lifetime statistics for the current game.
     * Supports tracking cards that possess multiple target types simultaneously (e.g., an Artifact Creature increments both counters).
     */
    private void recordTypeStats(UUID gameId, String playerName, MageObject mageObject, Game game) {
        // Retrieve or initialize the player's stat tracker for this specific game
        Map<String, Map<CardType, Integer>> playerStats = gameStats.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());
        Map<CardType, Integer> typeCounts = playerStats.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>());

        // Fetch the dynamic card types from the game state
        List<CardType> cardTypes = mageObject.getCardType(game);
        
        // We only care about tracking these six fundamental Magic: The Gathering card types
        Set<CardType> targetTypes = EnumSet.of(CardType.CREATURE, CardType.ARTIFACT, CardType.ENCHANTMENT, CardType.INSTANT, CardType.SORCERY, CardType.LAND);

        // Increment the count for every target type this card possesses
        for (CardType type : cardTypes) {
            if (targetTypes.contains(type)) {
                typeCounts.merge(type, 1, Integer::sum);
            }
        }
    }

    /**
     * Serializes the gathered card type statistics into a human-readable text file ("type_stats.txt")
     * within the game's output directory. 
     */
    private void writeTypeStatsToFile(Game game) {
        Map<String, Map<CardType, Integer>> playerStats = gameStats.get(game.getId());
        if (playerStats == null || playerStats.isEmpty()) {
            return; // No recorded plays (e.g., game aborted early or simulated without players)
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Card Type Stats for Game: ").append(game.getId()).append("\n");
        sb.append("========================================\n");

        // Sort player names alphabetically for consistent reporting
        List<String> sortedPlayers = new ArrayList<>(playerStats.keySet());
        Collections.sort(sortedPlayers);

        // The specific types and specific order requested for the output
        CardType[] targetTypes = {CardType.CREATURE, CardType.ARTIFACT, CardType.ENCHANTMENT, CardType.INSTANT, CardType.SORCERY, CardType.LAND};

        // Format each player's totals
        for (String playerName : sortedPlayers) {
            sb.append("Player: ").append(playerName).append("\n");
            Map<CardType, Integer> counts = playerStats.get(playerName);
            for (CardType type : targetTypes) {
                int count = counts.getOrDefault(type, 0); // Default to 0 if they played 0 cards of this type
                sb.append("  ").append(type.toString()).append(": ").append(count).append("\n");
            }
            sb.append("\n");
        }

        // Save the generated report to the game's designated output directory alongside the logs
        String gameDir = getOrCreateGameDir(game, isActive(game));
        if (!gameDir.isEmpty()) {
            Path statsFile = Paths.get(gameDir, "type_stats.txt");
            try {
                Files.write(statsFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.error("Can't write type_stats.txt for game " + game.getId() + ": " + e, e);
            }
        }
    }

    @Override
    public void onGameEnd(Game game) {
        if (!this.enabled) return;
        
        // Generate and save the final card type statistics file before the game directory 
        // is moved from "games_active" to "games_done".
        writeTypeStatsToFile(game);
        
        // Clean up memory to prevent a leak across multiple server games
        gameStats.remove(game.getId());

        writeToGameLogsFile(game, new Date() + " [END] " + game.getId() + ", " + game);

        // good end - move game data to done folder
        writeLock.lock();
        try {
            String oldFolder = getOrCreateGameDir(game, false);
            if (oldFolder.contains(DIR_NAME_GAMES_ACTIVE)) {
                // move files
                String newFolder = oldFolder.replace(DIR_NAME_GAMES_ACTIVE, DIR_NAME_GAMES_DONE);
                Files.createDirectories(Paths.get(newFolder));
                Files.move(Paths.get(oldFolder), Paths.get(newFolder), StandardCopyOption.REPLACE_EXISTING);

                // update all refs
                this.gameDirs.replaceAll((gameId, gameFolder) ->
                        gameFolder.startsWith(oldFolder)
                                ? gameFolder.replace(oldFolder, newFolder)
                                : gameFolder
                );
                innerDirDeleteEmptyParent(oldFolder);
            }
        } catch (IOException e) {
            logger.error("Can't move game files to done folder: " + e, e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void onChatTourney(UUID tourneyId, String userName, String message) {
        // TODO: implement?
    }

    @Override
    public void onChatTable(UUID tableId, String userName, String message) {
        if (!this.enabled) return;
        String needMessage = Jsoup.parse(message).text(); // convert html to txt format, so users can't break something
        writeToTableChatFile(tableId, new Date() + " [CHAT] " + (userName == null ? "system" : userName) + ": " + needMessage);
    }

    @Override
    public void onChatGame(UUID gameId, String userName, String message) {
        if (!this.enabled) return;
        String needMessage = Jsoup.parse(message).text(); // convert html to txt format, so users can't break something
        writeToGameChatFile(gameId, new Date() + " [CHAT] " + (userName == null ? "system" : userName) + ": " + needMessage);
    }


    private String getOrCreateTableDir(UUID tableId) {
        return this.tableDirs.getOrDefault(tableId, "");
    }

    private String getOrCreateTableDir(UUID tableId, UUID parentTableId, Integer tableIndex, boolean isActive) {
        // unit tests don't have tables, so write it to custom table
        String needName;
        UUID needId;
        if (tableId == null) {
            needId = NO_TABLE_ID;
            needName = String.format("table %s", NO_TABLE_NAME);
        } else {
            needId = tableId;
            if (parentTableId == null) {
                needName = String.format("table %s - %s", tableIndex, tableId);
            } else {
                needName = String.format("table %s - %s_%s", tableIndex, parentTableId, tableId);
            }
        }

        String needDate = DIR_DATE_FORMAT.format(new Date());
        String needStatus = isActive ? DIR_NAME_TABLES_ACTIVE : DIR_NAME_TABLES_DONE;

        String tableDir = Paths.get(
                DIR_NAME_ROOT,
                needDate,
                needStatus,
                needName
        ).toString();

        return this.tableDirs.computeIfAbsent(needId, x -> innerDirCreate(tableDir));
    }

    private String getOrCreateGameDir(UUID gameId) {
        // some events don't have full game info and must come after real game start
        return this.gameDirs.getOrDefault(gameId, "");
    }

    private String getOrCreateGameDir(Game game, boolean isActive) {
        AtomicBoolean isNewDir = new AtomicBoolean(false);
        String res = this.gameDirs.computeIfAbsent(game.getId(), x -> {
            isNewDir.set(true);
            // if you find "table 0 - UUID" folders with real server then game logs come before table then
            // it's almost impossible and nothing to do with it
            String tableDir = getOrCreateTableDir(game.getTableId(), null, 0, true);
            if (tableDir.isEmpty()) {
                // disabled
                return "";
            }

            String needStatus = isActive ? DIR_NAME_GAMES_ACTIVE : DIR_NAME_GAMES_DONE;
            String needName = String.format("game %s - %s", game.getGameIndex(), game.getId());
            String gameDir = Paths.get(
                    tableDir,
                    needStatus,
                    needName
            ).toString();

            return innerDirCreate(gameDir);
        });

        // workaround for good background color in html logs
        if (isNewDir.get()) {
            writeToGameLogsFile(game, "<body style=\"background: #862c10\">");
        }

        return res;
    }

    private String innerDirCreate(String destFileOrDir) {
        Path dir = Paths.get(destFileOrDir);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException ignore) {
                return "";
            }
        }
        return dir.toString();
    }

    private void innerDirDeleteEmptyParent(String dir) throws IOException {
        // delete empty parent folder, e.g. after move all games from active to done folder
        Path parentPath = Paths.get(dir).getParent();
        if (Files.exists(parentPath) && Files.isDirectory(parentPath)) {
            try (Stream<Path> entries = Files.list(parentPath)) {
                if (!entries.findAny().isPresent()) {
                    Files.delete(parentPath);
                }
            }
        }
    }

    private void writeToTableLogsFile(Table table, String data) {
        String tableDir = getOrCreateTableDir(table.getId(), table.getParentTableId(), table.getTableIndex(), isActive(table));
        if (tableDir.isEmpty()) {
            return;
        }
        writeToFile(Paths.get(tableDir, TABLE_LOGS_FILE_NAME).toString(), data, "\n");
    }

    private void writeToTableChatFile(UUID tableId, String data) {
        String gameDir = getOrCreateTableDir(tableId);
        if (gameDir.isEmpty()) {
            return;
        }
        writeToFile(Paths.get(gameDir, TABLE_CHAT_FILE_NAME).toString(), data, "\n");
    }

    private void writeToGameLogsFile(Game game, String data) {
        String gameDir = getOrCreateGameDir(game, isActive(game));
        if (gameDir.isEmpty()) {
            return;
        }
        writeToFile(Paths.get(gameDir, GAME_LOGS_FILE_NAME).toString(), data, "\n<br>\n");
    }

    private void writeToGameChatFile(UUID gameId, String data) {
        String gameDir = getOrCreateGameDir(gameId);
        if (gameDir.isEmpty()) {
            return;
        }
        writeToFile(Paths.get(gameDir, GAME_CHAT_FILE_NAME).toString(), data, "\n");
    }

    private void writeToFile(String destFile, String data, String newLine) {
        writeLock.lock();
        try {
            try {
                String newData = newLine + data;
                Files.write(Paths.get(destFile), newData.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignore) {
            }
        } finally {
            writeLock.unlock();
        }
    }

    private boolean isActive(Table table) {
        return !TableState.FINISHED.equals(table.getState());
    }

    private boolean isActive(Game game) {
        return game.getState() == null || !game.getState().isGameOver();
    }
}
