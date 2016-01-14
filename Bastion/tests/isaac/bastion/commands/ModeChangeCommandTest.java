package isaac.bastion.commands;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Isaac on 1/13/2016.
 */
public class ModeChangeCommandTest {


    @Test
    public void normalMode() throws Exception {
        ModeChangeCommand modeChangeCommand = new ModeChangeCommand(PlayersStates.Mode.INFO);
    }
}