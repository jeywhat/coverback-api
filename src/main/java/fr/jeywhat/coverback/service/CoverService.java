package fr.jeywhat.coverback.service;

import fr.jeywhat.coverback.helper.ChickenCoopAPIHelper;
import fr.jeywhat.coverback.helper.GameHelper;
import fr.jeywhat.coverback.model.ChikenCoopAPIModel;
import fr.jeywhat.coverback.model.GameInformation;
import fr.jeywhat.coverback.model.Game;
import fr.jeywhat.coverback.repository.GameRepository;
import fr.jeywhat.coverback.repository.model.GameEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Primary
public class CoverService {

    private static final Logger logger = LoggerFactory.getLogger(CoverService.class);

    @Value("${storage.location}")
    private String storageLocation;

    @Value("${default.img}")
    private String defaultImgName;

    @Value("${allow.download:false}")
    private boolean allowDownload;

    private GameRepository gameRepository;

    public CoverService(GameRepository gameRepository){
        this.gameRepository = gameRepository;
    }

    public GameEntity addGame(Game game){
        RestTemplate restTemplate = new RestTemplate();
        GameInformation gameInformation = new GameInformation();

        try{
            String fooResourceUrl
                    = ChickenCoopAPIHelper.requestBuilderURI(game.getName());
            ResponseEntity<ChikenCoopAPIModel> response = restTemplate.getForEntity(fooResourceUrl, ChikenCoopAPIModel.class);
            gameInformation = Objects.requireNonNull(response.getBody()).getResult();
        }catch(Exception e){
            logger.error("Can not retrieve game information : {}", game.getName());
        }

        return insertCoverIntoBDD(gameInformation, game);
    }

    public boolean removeGame(String gameTitle){
        gameRepository.deleteById(gameTitle);
        return true;
    }

    public Optional<GameEntity> findGameByID(String name){
        return gameRepository.findById(name);
    }

    public List<GameEntity> findAllGames(){
        return gameRepository.findAll();
    }


    public ResponseEntity<InputStreamResource> downloadGame(String name){
        if(!allowDownload){
            //By default, file download is not enabled
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<GameEntity> game = findGameByID(name);
        if(game.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        File file = new File(game.get().getFullpath());
        InputStreamResource resource;
        try {
            resource = new InputStreamResource(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName()+ "\"")
                .contentLength(file.length())
                .body(resource);
    }

    public boolean refreshGame(boolean justCoverNull){
        List<GameEntity> gameEntities;

        if(justCoverNull){
            gameEntities = gameRepository.findGameEntitiesByImageIsNullOrImageEqualsOrImageEquals(new byte[0], GameHelper.getBytesImageURI(null, defaultImgName));
        }else{
            gameEntities = gameRepository.findAll();
        }

        new Thread(() -> gameEntities.forEach(g -> this.addGame(new Game(g.getFullpath())))).start();

        return true;
    }

    @Transactional
    public GameEntity insertCoverIntoBDD(GameInformation gameInformation, Game game){
        GameEntity gameEntity = GameEntity.builder()
                .namefile(game.getName())
                .superXCI(game.isSuperXCI())
                .nbDLC(game.getNbDLC())
                .version(game.getVersion())
                .fullpath(game.getFullpath())
                .extension(game.getExtension())
                .size(game.getSize())
                .title(gameInformation.getTitle() == null || gameInformation.getTitle().isEmpty() ? game.getName() : gameInformation.getTitle())
                .releaseDate(gameInformation.getReleaseDate())
                .genre(String.join(", ", gameInformation.getGenre()))
                .developer(gameInformation.getDeveloper()).score(gameInformation.getScore())
                .rating(gameInformation.getRating())
                .image(GameHelper.getBytesImageURI(gameInformation.getImage(), defaultImgName))
                .canBeDownloaded(allowDownload)
                .createOn(new Date())
                .build();
        gameRepository.save(gameEntity);
        logger.info("Inserted : "+game.getName());
        return gameEntity;
    }
}
