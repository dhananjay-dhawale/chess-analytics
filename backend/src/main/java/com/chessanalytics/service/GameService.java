package com.chessanalytics.service;

import com.chessanalytics.model.Game;
import com.chessanalytics.repository.GameRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    /**
     * Saves a game if it doesn't already exist (based on hash).
     *
     * @return true if saved, false if duplicate
     */
    @Transactional
    public boolean saveIfNotDuplicate(Game game) {
        if (gameRepository.existsByAccountIdAndPgnHash(
                game.getAccount().getId(), game.getPgnHash())) {
            return false;
        }
        gameRepository.save(game);
        return true;
    }

    /**
     * Returns paginated games for an account.
     */
    @Transactional(readOnly = true)
    public Page<Game> getGamesForAccount(Long accountId, Pageable pageable) {
        return gameRepository.findByAccountIdOrderByPlayedAtDesc(accountId, pageable);
    }
}
