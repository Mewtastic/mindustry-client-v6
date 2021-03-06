package mindustry.ui.fragments;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.client.FooUser;
import mindustry.client.Spectate;
import mindustry.client.navigation.AssistPath;
import mindustry.client.navigation.Navigation;
import mindustry.client.navigation.UnAssistPath;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.DesktopInput;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class PlayerListFragment extends Fragment{
    public Table content = new Table().marginRight(13f).marginLeft(13f);
    private boolean visible = false;
    private Interval timer = new Interval();
    private TextField sField;
    private boolean found = false;

    @Override
    public void build(Group parent){
        content.name = "players";
        parent.fill(cont -> {
            cont.name = "playerlist";
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!(net.active() && state.isGame())){
                    visible = false;
                    return;
                }

                if(visible && timer.get(60)){
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    //hacky
                    Core.scene.act(0f);
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format(Groups.player.size() == 1 ? "players.single" : "players", Groups.player.size()));
                pane.row();
                sField = pane.field(null, text -> {
                    rebuild();
                }).grow().pad(8).get();
                sField.name = "search";
                sField.setMaxLength(maxNameLength);
                sField.setMessageText(Core.bundle.format("players.search"));

                pane.row();
                pane.pane(content).grow().get().setScrollingDisabled(true, false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().growX().height(50f).fillY();
                    menu.name = "menu";

                    menu.button("@server.bans", ui.bans::show).disabled(b -> net.client());
                    menu.button("@server.admins", ui.admins::show).disabled(b -> net.client());
                    menu.button("@close", this::toggle);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f);
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 74f;
        found = false;

        Groups.player.sort(Structs.comparing(Player::team));
        Groups.player.each(user -> {
            found = true;
            NetConnection connection = user.con;

            if(connection == null && net.server() && !user.isLocal()) return;
            if(sField.getText().length() > 0 && !user.name().toLowerCase().contains(sField.getText().toLowerCase()) && !Strings.stripColors(user.name().toLowerCase()).contains(sField.getText().toLowerCase())) return;

            Table button = new Table();
            button.left();
            button.margin(5).marginBottom(10);

            Table table = new Table(){
                @Override
                public void draw(){
                    super.draw();
                    Draw.color(Pal.gray);
                    Draw.alpha(parentAlpha);
                    Lines.stroke(Scl.scl(4f));
                    Lines.rect(x, y, width, height);
                    Draw.reset();
                }
            };
            table.margin(8);
            table.add(new Image(user.icon()).setScaling(Scaling.bounded)).grow();
            table.name = user.name();

            button.add(table).size(h);
            button.labelWrap("[#" + user.color().toString().toUpperCase() + "]" + user.name()).width(300f).pad(10);
            button.add().grow();

            button.image(Icon.admin).visible(() -> user.admin && !(!user.isLocal() && net.server())).padRight(5).get().updateVisibility();
            button.image(Icon.wrench).visible(() -> FooUser.IsUser(user) && !(!user.isLocal() && net.server())).padRight(10).get().updateVisibility();

            if((net.server() || player.admin) && !user.isLocal() && (!user.admin || net.server())){
                button.add().growY();

                float bs = (h) / 2f;

                button.table(t -> {
                    t.defaults().size(bs);

                    t.button(Icon.hammer, Styles.clearPartiali,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmban",  user.name()), () -> Call.adminRequest(user, AdminAction.ban)));
                    t.button(Icon.cancel, Styles.clearPartiali,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmkick",  user.name()), () -> Call.adminRequest(user, AdminAction.kick)));

                    t.row();

                    t.button(Icon.admin, Styles.clearTogglePartiali, () -> {
                        if(net.client()) return;

                        String id = user.uuid();

                        if(netServer.admins.isAdmin(id, connection.address)){
                            ui.showConfirm("@confirm", Core.bundle.format("confirmunadmin",  user.name()), () -> netServer.admins.unAdminPlayer(id));
                        }else{
                            ui.showConfirm("@confirm", Core.bundle.format("confirmadmin",  user.name()), () -> netServer.admins.adminPlayer(id, user.usid()));
                        }
                    }).update(b -> b.setChecked(user.admin))
                        .disabled(b -> net.client())
                        .touchable(() -> net.client() ? Touchable.disabled : Touchable.enabled)
                        .checked(user.admin);

                    t.button(Icon.zoom, Styles.clearPartiali, () -> Call.adminRequest(user, AdminAction.trace));

                }).padRight(12).size(bs + 10f, bs);
            }else if(!user.isLocal() && !user.admin && net.client() && Groups.player.size() >= 3 && player.team() == user.team()){ //votekick
                button.add().growY();

                button.button(Icon.hammer, Styles.clearPartiali,
                () -> {
                    ui.showConfirm("@confirm", Core.bundle.format("confirmvotekick",  user.name()), () -> {
                        Call.sendChatMessage("/votekick " + user.name());
                    });
                }).size(h/2);
            }
            button.button(Icon.copy, Styles.clearPartiali, // Assist/copy
                    () -> Navigation.follow(new AssistPath(user))).size(h/2);
            button.button(Icon.cancel, Styles.clearPartiali, // Unassist/block
                    () -> Navigation.follow(new UnAssistPath(user))).size(h/2);
            button.button(Icon.move, Styles.clearPartiali, // Goto
                    () -> Navigation.navigateTo(user.x, user.y)).size(h/2);
            button.button(Icon.zoom, Styles.clearPartiali, // Spectate/stalk
                    () -> {
                DesktopInput.panning = true;
                Spectate.pos = user;
            });

            content.add(button).padBottom(-6).width(600f).maxHeight(h + 14);
            content.row();
            content.image().height(4f).color(state.rules.pvp ? user.team().color : Pal.gray).growX();
            content.row();
        });

        if(!found){
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(600f).maxHeight(h + 14);
        }

        content.marginBottom(5);
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            sField.clearText();
        }
    }

}
