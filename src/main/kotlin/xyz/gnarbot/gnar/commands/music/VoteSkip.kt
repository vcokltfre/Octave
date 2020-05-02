package xyz.gnarbot.gnar.commands.music

import me.devoxin.flight.api.Context
import me.devoxin.flight.api.annotations.Command
import net.dv8tion.jda.api.EmbedBuilder
import xyz.gnarbot.gnar.utils.desc
import xyz.gnarbot.gnar.utils.description
import xyz.gnarbot.gnar.utils.extensions.*
import xyz.gnarbot.gnar.utils.field
import xyz.gnarbot.gnar.utils.getDisplayValue
import xyz.gnarbot.gnar.utils.commands.helpers.MusicCog
import java.util.concurrent.TimeUnit

class VoteSkip : MusicCog(true, true, true) {
    @Command(description = "Vote to skip the current music track.")
    suspend fun voteskip(ctx: Context) {
        if(ctx.member!!.voiceState!!.isDeafened)
            return ctx.send("You actually have to be listening to the song to start a vote.")

        if(ctx.manager.isVotingToSkip)
            return ctx.send("There's already a vote going on.")

        val voteSkipCooldown = if(ctx.data.music.voteSkipCooldown == 0L) {
            ctx.config.voteSkipCooldown.toMillis()
        } else {
            ctx.data.music.voteSkipCooldown
        }

        val voteSkipCooldownText = if(ctx.data.music.voteSkipCooldown == 0L) {
            ctx.config.voteSkipCooldownText
        } else {
            getDisplayValue(ctx.data.music.voteSkipCooldown)
        }

        if (System.currentTimeMillis() - ctx.manager.lastVoteTime < voteSkipCooldown)
             return ctx.send("You must wait $voteSkipCooldownText before starting a new vote.")

        val voteSkipDuration = if(ctx.data.music.voteSkipDuration == 0L) {
            ctx.config.voteSkipDuration.toMillis()
        } else {
            ctx.data.music.voteSkipDuration
        }

        val voteSkipDurationText = if(ctx.data.music.voteSkipDuration == 0L) {
            ctx.config.voteSkipDurationText
        } else {
            val durationMinutes = ctx.config.voteSkipDuration.toMinutes();
            if(durationMinutes > 0)
                "$durationMinutes minutes"
            else
                "${ctx.config.voteSkipDuration.toSeconds()} seconds"
        }

        if (ctx.manager.player.playingTrack.duration - ctx.manager.player.playingTrack.position <= voteSkipDuration)
            return ctx.send("By the time the vote finishes in $voteSkipDurationText, the song will be over.")

        ctx.manager.lastVoteTime = System.currentTimeMillis()
        ctx.manager.isVotingToSkip = true
        val halfPeople = ctx.selfMember!!.voiceState!!.channel!!.members.filterNot { it.user.isBot }.size / 2

        ctx.sendAsync {
            setTitle("Vote Skip")
            description {
                buildString {
                    append(ctx.message.author.asMention)
                    append(" has voted to **skip** the current track!")
                    append(" React with :thumbsup:\n")
                    append("If at least **${halfPeople + 1}** vote(s) from listeners are obtained " +
                            "within **$voteSkipDurationText**, the song will be skipped!")
                }
            }
        }.let { message ->
            message.addReaction("👍")
                    .submit()
                    .thenApply { message }
                    .thenCompose {
                        it.editMessage(EmbedBuilder(it.embeds[0])
                                .apply {
                                    desc { "Voting has ended! Check the newer messages for results." }
                                    clearFields()
                                }.build()
                        ).submitAfter(voteSkipDuration, TimeUnit.MILLISECONDS)
                    }.thenAccept { m ->
                        val skip = m.reactions.firstOrNull { it.reactionEmote.name == "👍" }?.count?.minus(1) ?: 0

                        ctx.send {
                            desc {
                                setTitle("Vote Skip")
                                buildString {
                                    if (skip > halfPeople) {
                                        appendln("The vote has passed! The song has been skipped.")
                                        ctx.manager.scheduler.nextTrack()
                                    } else {
                                        appendln("The vote has failed! The song will stay.")
                                    }
                                }
                            }

                            field("Results") {
                                "__$skip Skip Votes__"
                            }
                        }
                    }.whenComplete { _, _ ->
                        ctx.manager.isVotingToSkip = false
                    }
        }
    }
}