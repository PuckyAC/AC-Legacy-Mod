package dev.adventurecraft.awakening.entity;

import dev.adventurecraft.awakening.common.Coord;
import dev.adventurecraft.awakening.common.IEntityPather;
import dev.adventurecraft.awakening.extension.entity.ExMob;
import dev.adventurecraft.awakening.extension.entity.ai.pathing.ExEntityPath;
import dev.adventurecraft.awakening.extension.util.io.ExCompoundTag;
import dev.adventurecraft.awakening.extension.world.ExWorld;
import dev.adventurecraft.awakening.script.EntityDescriptions;
import dev.adventurecraft.awakening.script.ScopeTag;
import dev.adventurecraft.awakening.script.ScriptEntity;
import dev.adventurecraft.awakening.script.ScriptEntityDescription;
import dev.adventurecraft.awakening.tile.entity.AC_TileEntityNpcPath;
import dev.adventurecraft.awakening.util.MathF;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class AC_EntityLivingScript extends Mob implements IEntityPather {

    String initDescTo;
    String descriptionName;
    private float prevWidth = 0.6F;
    private float prevHeight = 1.8F;
    protected Scriptable scope;
    public String onCreated = "";
    public String onUpdate = "";
    public String onPathReached = "";
    public String onAttacked = "";
    public String onDeath = "";
    public String onInteraction = "";
    private Path path;
    private Entity pathToEntity;
    private @Nullable Coord pathToVec;
    public float maxPathDistance = 64.0F;
    private int nextPathIn;
    private double prevDistToPoint = 999999.0D;
    private AC_TileEntityNpcPath triggerOnPath = null;

    public AC_EntityLivingScript(Level world) {
        super(world);
        this.scope = ((ExWorld) world).getScript().getNewScope();
        Object jsEntity = Context.javaToJS(ScriptEntity.getEntityClass(this), this.scope);
        ScriptableObject.putProperty(this.scope, "entity", jsEntity);
    }

    public void setEntityDescription(String name) {
        this.setEntityDescription(name, true);
    }

    private void setEntityDescription(String name, boolean reset) {
        this.descriptionName = name;
        ScriptEntityDescription desc = EntityDescriptions.getDescription(name);
        if (desc == null) {
            return;
        }

        if (reset) {
            this.health = desc.health;
            ((ExMob) this).setMaxHealth(desc.health);
            this.onCreated = desc.onCreated;
            this.onUpdate = desc.onUpdate;
            this.onPathReached = desc.onPathReached;
            this.onAttacked = desc.onAttacked;
            this.onDeath = desc.onDeath;
            this.onInteraction = desc.onInteraction;
        }

        this.bbWidth = desc.width;
        this.bbHeight = desc.height;
        this.textureName = desc.texture;
        this.runSpeed = desc.moveSpeed;
        this.runCreatedScript();
    }

    @Override
    protected void serverAiStep() {
    }

    @Override
    public void tick() {
        if (this.initDescTo != null) {
            if (!this.initDescTo.isEmpty()) {
                this.setEntityDescription(this.initDescTo, false);
            }

            this.initDescTo = null;
        }

        this.prevWidth = this.bbWidth;
        this.prevHeight = this.bbHeight;
        this.continuePathing();
        this.runUpdateScript();
        super.tick();
    }

    @Override
    public boolean hurt(Entity entity, int damage) {
        Object jsEntity = Context.javaToJS(ScriptEntity.getEntityClass(entity), this.scope);
        ScriptableObject.putProperty(this.scope, "attackingEntity", jsEntity);
        ScriptableObject.putProperty(this.scope, "attackingDamage", damage);
        return this.runOnAttackedScript() && super.hurt(entity, damage);
    }

    @Override
    public void remove() {
        super.remove();
        this.runDeathScript();
    }

    @Override
    public boolean interact(Player entity) {
        return this.runOnInteractionScript();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        var exTag = (ExCompoundTag) tag;
        exTag.putNonEmptyString("descriptionName", this.descriptionName);
        exTag.putNonEmptyString("onCreated", this.onCreated);
        exTag.putNonEmptyString("onUpdate", this.onUpdate);
        exTag.putNonEmptyString("onPathReached", this.onPathReached);
        exTag.putNonEmptyString("onAttacked", this.onAttacked);
        exTag.putNonEmptyString("onDeath", this.onDeath);
        exTag.putNonEmptyString("onInteraction", this.onInteraction);

        tag.putTag("scope", ScopeTag.getTagFromScope(this.scope));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.initDescTo = tag.getString("descriptionName");
        this.onCreated = tag.getString("onCreated");
        this.onUpdate = tag.getString("onUpdate");
        this.onPathReached = tag.getString("onPathReached");
        this.onAttacked = tag.getString("onAttacked");
        this.onDeath = tag.getString("onDeath");
        this.onInteraction = tag.getString("onInteraction");

        ((ExCompoundTag) tag).findCompound("scope").ifPresent(c -> ScopeTag.loadScopeFromTag(this.scope, c));
    }

    public void runCreatedScript() {
        if (!this.onCreated.isEmpty()) {
            ((ExWorld) this.level).getScriptHandler().runScript(this.onCreated, this.scope);
        }
    }

    private void runUpdateScript() {
        if (!this.onUpdate.isEmpty()) {
            ((ExWorld) this.level).getScriptHandler().runScript(this.onUpdate, this.scope);
        }
    }

    private void runPathCompletedScript() {
        if (!this.onPathReached.isEmpty()) {
            ((ExWorld) this.level).getScriptHandler().runScript(this.onPathReached, this.scope);
        }
    }

    private boolean runOnAttackedScript() {
        if (this.onAttacked.isEmpty()) {
            return true;
        }

        Object result = ((ExWorld) this.level).getScriptHandler().runScript(this.onAttacked, this.scope);
        return result instanceof Boolean b ? b : true;
    }

    private void runDeathScript() {
        if (!this.onDeath.isEmpty()) {
            ((ExWorld) this.level).getScriptHandler().runScript(this.onDeath, this.scope);
        }
    }

    private boolean runOnInteractionScript() {
        if (!this.onInteraction.isEmpty()) {
            Object result = ((ExWorld) this.level).getScriptHandler().runScript(this.onInteraction, this.scope);
            return result instanceof Boolean b ? b : true;
        }
        return true;
    }

    public boolean isPathing() {
        return this.pathToEntity != null || this.pathToVec != null || this.path != null;
    }

    public void pathToEntity(Entity entity) {
        this.pathToEntity = entity;
        this.pathToVec = null;
        this.path = this.level.findPath(this, this.pathToEntity, this.maxPathDistance);
        this.nextPathIn = this.level.random.nextInt(40) + 60;
        this.prevDistToPoint = 999999.0D;
        this.setTriggerOnPath(null);
    }

    public void pathToPosition(int x, int y, int z) {
        this.pathToEntity = null;
        this.pathToVec = new Coord(x, y, z);
        this.path = this.level.findPath(this, x, y, z, this.maxPathDistance);
        this.nextPathIn = this.level.random.nextInt(40) + 60;
        this.prevDistToPoint = 999999.0D;
        this.setTriggerOnPath(null);
    }

    public void clearPathing() {
        this.pathToEntity = null;
        this.pathToVec = null;
        this.path = null;
        this.setTriggerOnPath(null);
        this.zza = 0.0F;
    }

    private void continuePathing() {
        if (!this.isPathing()) {
            return;
        }

        if (this.path == null || --this.nextPathIn <= 0 && this.pathToEntity != null &&
            ((ExEntityPath) this.path).needNewPath(this.pathToEntity)) {
            if (this.pathToEntity != null) {
                this.path = this.level.findPath(this, this.pathToEntity, this.maxPathDistance);
            }
            else {
                Coord p = this.pathToVec;
                if (p != null) {
                    this.path = this.level.findPath(this, p.x, p.y, p.z, this.maxPathDistance);
                }
            }

            this.nextPathIn = this.level.random.nextInt(40) + 10;
            this.prevDistToPoint = 999999.0D;
        }

        if (this.path == null) {
            return;
        }

        Vec3 point = this.path.current(this);
        this.zza = 0.0F;
        this.jumping = false;
        double dist = point.distanceToSqr(this.x, point.y, this.z);
        if (dist >= this.prevDistToPoint && this.nextPathIn > 5) {
            this.nextPathIn = this.level.random.nextInt(5) + 1;
        }

        this.prevDistToPoint = dist;

        double maxDist = (double) this.bbWidth * 1.1D;
        maxDist *= maxDist;

        while (point != null && point.distanceToSqr(this.x, point.y, this.z) < maxDist) {
            this.path.next();
            if (this.path.isDone()) {
                point = null;
                this.path = null;
                this.runPathCompletedScript();
                if (this.getTriggerOnPath() != null) {
                    this.getTriggerOnPath().pathFinished();
                }
                return;
            }
            point = this.path.current(this);
            this.prevDistToPoint = 999999.0D;
        }

        if (point != null) {
            double dX = point.x - this.x;
            double dZ = point.z - this.z;
            double dY = point.y - (double) Mth.floor(this.bb.y0 + 0.5D);
            float newYaw = (float) (Math.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F;
            float extraYaw = newYaw - this.yRot;

            this.zza = this.runSpeed;
            while (extraYaw < -180.0F) {
                extraYaw += 360.0F;
            }

            while (extraYaw >= 180.0F) {
                extraYaw -= 360.0F;
            }

            extraYaw = MathF.clamp(extraYaw, -30.0F, 30.0F);

            this.yRot += extraYaw;
            if (dY > 0.0D) {
                this.jumping = true;
            }
        }
    }

    @Override
    public Path getCurrentPath() {
        return this.path;
    }

    public AC_TileEntityNpcPath getTriggerOnPath() {
        return this.triggerOnPath;
    }

    public void setTriggerOnPath(AC_TileEntityNpcPath path) {
        this.triggerOnPath = path;
    }

    public float getPrevWidth() {
        return prevWidth;
    }

    public float getPrevHeight() {
        return prevHeight;
    }
}
