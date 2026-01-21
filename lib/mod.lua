-- smpKit v1.0 - nb voice to play ur smpls - @sonoCircuit

local fs = require 'fileselect'
local tx = require 'textentry'
local mu = require 'musicutil'
local md = require 'core/mods'

local preset_path = "/home/we/dust/data/nb_smpkit/smpkit_kits"
local audio_path = "/home/we/dust/audio/"
local current_kit = ""

local NUM_VOICES = 12
local MAX_LENGTH = math.pow(2, 24) -- approx 5.8min @48k (sc phasor resolution)

local is_active = false
local load_queue = {}
local loading_samples = false
local track_oct = false
local root_note = 0
local play_mode = {}
local is_playing = {}
for i = 1, NUM_VOICES do
  table.insert(play_mode, 1)
  table.insert(is_playing, false)
end

local paramslist = {
  "load_sample", "amp", "pan", "dist", "send_a", "send_b",
  "mode", "pitch", "tune", "dir", "rate_slew",
  "start", "length", "fade_in", "fade_out",
  "lpf_hz", "lpf_rz", "eq_freq", "eq_q", "eq_amp", "hpf_hz", "hpf_rz"
}


--------------------------- osc msgs ---------------------------

local function init_smpkit()
  osc.send({ "localhost", 57120 }, "/nb_smpkit/init")
end

local function reset_queue()
  osc.send({ "localhost", 57120 }, "/nb_smpkit/reset_loadqueue")
end

local function load_buffer(i, path)
  local vox = i - 1
  osc.send({ "localhost", 57120 }, "/nb_smpkit/load_buffer", {vox, path})
end

local function clear_buffer(i)
  local vox = i - 1
  osc.send({ "localhost", 57120 }, "/nb_smpkit/clear_buffer", {vox})
  params:set("nb_smpkit_load_sample_"..i, audio_path)
end

local function free_buffers()
  osc.send({ "localhost", 57120 }, "/nb_smpkit/free_buffers")
end

local function trig_voice(vox, vel, oct)
  osc.send({ "localhost", 57120 }, "/nb_smpkit/trig", {vox, vel, oct})
end

local function stop_voice(vox)
  osc.send({ "localhost", 57120 }, "/nb_smpkit/stop", {vox})
end

local function dont_panic()
  osc.send({ "localhost", 57120 }, "/nb_smpkit/panic")
end

local function set_main_amp(val)
  osc.send({ "localhost", 57120 }, "/nb_smpkit/set_level", {val})
end

local function set_param(i, key, val)
  local vox = i - 1
  osc.send({ "localhost", 57120 }, "/nb_smpkit/set_param", {vox, key, val})
end


--------------------------- utils ---------------------------

local function round_form(param, quant, form)
  return(util.round(param, quant)..form)
end

local function pan_display(param)
  if param < -0.01 then
    return ("L < "..math.abs(util.round(param * 100, 1)))
  elseif param > 0.01 then
    return (math.abs(util.round(param * 100, 1)).." > R")
  else
    return "> <"
  end
end

local function build_menu(voice)
  for i = 1, NUM_VOICES do
    local state = voice == i and "show" or "hide"
    for _, v in pairs(paramslist) do
      params[state](params, "nb_smpkit_"..v.."_"..i)
    end
    params[state](params, "nb_smpkit_clear_sample_"..i)
    if not md.is_loaded("fx") then
      params:hide("nb_smpkit_send_a_"..i)
      params:hide("nb_smpkit_send_b_"..i)
    end
  end
  _menu.rebuild_params()
end

local function set_loop(i, key)
  local s = params:get("nb_smpkit_start_"..i)
  local l = params:get("nb_smpkit_length_"..i)
  if s + l > 1 then
    if key == "start" then
      params:set("nb_smpkit_length_"..i, 1 - s)
    elseif key == "length" then
      params:set("nb_smpkit_start_"..i, 1 - l)
    end
  end
end


--------------------------- file management ---------------------------

local function queue_sample_load(i, path)
  if is_active then
    if (path ~= "cancel" and path ~= "" and path ~= _path.audio) then
      local ch, samples = audio.file_info(path)
      if ch > 0 and ch < 3 and samples > 1 then
        if samples < MAX_LENGTH then
          load_buffer(i, path)
        else
          print("max length exceeded: "..path)
        end
      else
        print("file not supported: "..path)
      end
    end
  end
end

local function alloc_buffers()
  reset_queue()
  for i = 1, NUM_VOICES do
    queue_sample_load(i, params:get("nb_smpkit_load_sample_"..i))
  end
end

local function getfiles(directory)
  local fp = util.scandir(directory)
  local tp = {table.unpack(fp)}
  for i, f in ipairs(tp) do
    if f:match("[/]$") then
      table.remove(fp, tab.key(fp, f))
    end
  end
  return fp, #fp
end

local function batchload(path)
  local file = path:match("[^/]*$")
  local directory = path:match("(.*[/])")
  local flist, fnum = getfiles(directory)
  local fstart = 0
  -- get start index
  for i, p in ipairs(flist) do
    if p == file then
      fstart = i
      goto continue
    end
  end
  ::continue::
  for i = 1, NUM_VOICES do
    local f = fstart + (i - 1)
    if f > fnum then f = f - fnum end
    if flist[f] ~= nil then
      params:set("nb_smpkit_load_sample_"..i, directory..flist[f])
    else
      print("not a file")
    end
  end
end


--------------------------- save and load ---------------------------

local function save_kit(txt)
  if txt then
    local kit = {}
    kit.root = params:get("nb_smpkit_root")
    kit.track = params:get("nb_smpkit_track")
    kit.level = params:get("nb_smpkit_main_amp")
    for i = 1, NUM_VOICES do
      kit[i] = {}
      for _, v in pairs(paramslist) do
        kit[i][v] = params:get("nb_smpkit_"..v.."_"..i)
      end
    end
    clock.run(function()
      clock.sleep(0.2)
      tab.save(kit, preset_path.."/"..txt..".kit")
      current_kit = txt
      print("saved smpkit: "..txt)
    end)
  end
end

local function load_kit(path)
  if path ~= "cancel" and path ~= "" and path ~= preset_path then
    dont_panic()
    if path:match("^.+(%..+)$") == ".kit" then
      local kit = tab.load(path)
      if kit ~= nil then
        params:set("nb_smpkit_root", kit.root)
        params:set("nb_smpkit_track", kit.track)
        params:set("nb_smpkit_main_amp", kit.level)
        for i = 1, NUM_VOICES do
          for _, v in pairs(paramslist) do
            params:set("nb_smpkit_"..v.."_"..i, kit[i][v])
            if v == "load_sample" and kit[i][v] == audio_path then
              params:set("nb_smpkit_clear_sample_"..i, 1)
            end
          end
        end
        current_kit = path:match("[^/]*$"):gsub(".kit", "")
        print("loaded smpkit: "..current_kit)
      else
        print("error: could not find kit", path)
      end
    else
      print("error: not a smpkit file")
    end
  end
end


--------------------------- params ---------------------------

local function add_smpkit_params()
  params:add_group("nb_smpkit_group", "smpkit", 13 + 23 * NUM_VOICES)
  params:hide("nb_smpkit_group")

  params:add_separator("nb_smpkit_presets", "presets")

  params:add_trigger("nb_smpkit_load", ">> load")
  params:set_action("nb_smpkit_load", function(path) fs.enter(preset_path, function(path) load_kit(path) end) end)

  params:add_trigger("nb_smpkit_save", "<< save")
  params:set_action("nb_smpkit_save", function() tx.enter(save_kit, current_kit) end)

  params:add_separator("nb_smpkit_globals", "global settings")

  params:add_trigger("nb_smpkit_batchload", ">> batch load")
  params:set_action("nb_smpkit_batchload", function(path) fs.enter(audio_path, function(path) batchload(path) end) end)

  params:add_number("nb_smpkit_root", "root note", 0, 84, 48, function(param) return mu.note_num_to_name(param:get(), track_oct) end)
  params:set_action("nb_smpkit_root", function(val) root_note = val end)

  params:add_option("nb_smpkit_track", "track octave", {"no", "yes"}, 1)
  params:set_action("nb_smpkit_track", function(val) track_oct = val == 2 and true or false end)

  params:add_control("nb_smpkit_main_amp", "main level", controlspec.new(0, 1, "lin", 0, 1), function(param) return round_form(param:get() * 100, 1, "%") end)
  params:set_action("nb_smpkit_main_amp", function(val) set_main_amp(val) end)

  params:add_separator("nb_smpkit_voice", "smpkit voice")

  params:add_number("nb_smpkit_focus", "selected voice", 1, NUM_VOICES, 1)
  params:set_action("nb_smpkit_focus", function(voice) build_menu(voice) end)

  for i = 1, NUM_VOICES do
    params:add_file("nb_smpkit_load_sample_"..i, "load", audio_path)
    params:set_action("nb_smpkit_load_sample_"..i, function(path) queue_sample_load(i, path) end)

    params:add_binary("nb_smpkit_clear_sample_"..i, "clear", "trigger")
    params:set_action("nb_smpkit_clear_sample_"..i, function() clear_buffer(i) end)
  end

  params:add_separator("nb_smpkit_levels", "levels")

  for i = 1, NUM_VOICES do
    params:add_control("nb_smpkit_amp_"..i, "level", controlspec.new(0, 1, "lin", 0, 0.8), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_amp_"..i, function(val) set_param(i, 'amp', val) end)

    params:add_control("nb_smpkit_dist_"..i, "drive", controlspec.new(0, 1, "lin", 0, 0), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_dist_"..i, function(val) set_param(i, 'dist', val) end)

    params:add_control("nb_smpkit_pan_"..i, "pan", controlspec.new(-1, 1, "lin", 0, 0), function(param) return pan_display(param:get()) end)
    params:set_action("nb_smpkit_pan_"..i, function(val) set_param(i, 'pan', val) end)

    params:add_control("nb_smpkit_send_a_"..i, "send a", controlspec.new(0, 1, "lin", 0, 0), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_send_a_"..i, function(val) set_param(i, 'sendA', val) end)
    
    params:add_control("nb_smpkit_send_b_"..i, "send b", controlspec.new(0, 1, "lin", 0, 0), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_send_b_"..i, function(val) set_param(i, 'sendB', val) end)
  end

  params:add_separator("nb_smpkit_playback", "playback")

  for i = 1, NUM_VOICES do
    params:add_option("nb_smpkit_mode_"..i, "mode", {"oneshot", "hold", "toggle"}, 1)
    params:set_action("nb_smpkit_mode_"..i, function(val) set_param(i, 'mode', val == 1 and 0 or 1) play_mode[i] = val end)

    params:add_number("nb_smpkit_pitch_"..i, "pitch", -24, 24, 0, function(param) local val = param:get() return val.."st" end)
    params:set_action("nb_smpkit_pitch_"..i, function(val) set_param(i, 'pitch', val) end)

    params:add_number("nb_smpkit_tune_"..i, "tune", -100, 100, 0, function(param) local val = param:get() return val.."cent" end)
    params:set_action("nb_smpkit_tune_"..i, function(val) set_param(i, 'tune', val / 100) end)

    params:add_option("nb_smpkit_dir_"..i, "direction", {"rev", "fwd"}, 2)
    params:set_action("nb_smpkit_dir_"..i, function(val) set_param(i, 'plyDir', val == 1 and -1 or 1) end)

    params:add_control("nb_smpkit_rate_slew_"..i, "rate slew", controlspec.new(0, 2, "lin", 0, 0), function(param) return round_form(param:get(), 0.01, "s") end)
    params:set_action("nb_smpkit_rate_slew_"..i, function(val) set_param(i, 'rateSlew', val) end)

    params:add_control("nb_smpkit_start_"..i, "start", controlspec.new(0, 1, "lin", 0, 0), function(param) return round_form(param:get() * 100, 0.1, "%") end)
    params:set_action("nb_smpkit_start_"..i, function(val) set_param(i, 'srtRel', val) set_loop(i, 'start') end)

    params:add_control("nb_smpkit_length_"..i, "length", controlspec.new(0, 1, "lin", 0, 1), function(param) return round_form(param:get() * 100, 0.1, "%") end)
    params:set_action("nb_smpkit_length_"..i, function(val) set_param(i, 'lenRel', val) set_loop(i, 'length') end)

    params:add_control("nb_smpkit_fade_in_"..i, "fade in", controlspec.new(0.01, 2, "lin", 0, 0), function(param) return round_form(param:get(), 0.01, "s") end)
    params:set_action("nb_smpkit_fade_in_"..i, function(val) set_param(i, 'fadeIn', val) end)

    params:add_control("nb_smpkit_fade_out_"..i, "fade out", controlspec.new(0.01, 2, "lin", 0, 0), function(param) return round_form(param:get(), 0.01, "s") end)
    params:set_action("nb_smpkit_fade_out_"..i, function(val) set_param(i, 'fadeOut', val) end)
  end
  
  params:add_separator("nb_smpkit_filters", "filters")

  for i = 1, NUM_VOICES do
    params:add_control("nb_smpkit_lpf_hz_"..i, "lpf cutoff", controlspec.new(60, 20000, "exp", 0, 20000), function(param) return round_form(param:get(), 1, "hz") end)
    params:set_action("nb_smpkit_lpf_hz_"..i, function(val) set_param(i, 'lpfHz', val) end)

    params:add_control("nb_smpkit_lpf_rz_"..i, "lpf resonance", controlspec.new(0, 1, "lin", 0, 0.2), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_lpf_rz_"..i, function(val) set_param(i, 'lpfRz', val) end)

    params:add_control("nb_smpkit_eq_freq_"..i, "eq freq", controlspec.new(120, 12000, "exp", 0, 1200), function(param) return round_form(param:get(), 1, "hz") end)
    params:set_action("nb_smpkit_eq_freq_"..i, function(val) set_param(i, 'eqHz', val) end)

    params:add_control("nb_smpkit_eq_q_"..i, "eq q", controlspec.new(0, 1, "lin", 0, 0.2), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_eq_q_"..i, function(val) set_param(i, 'eqQ', val) end)

    params:add_control("nb_smpkit_eq_amp_"..i, "eq amp", controlspec.new(-12, 12, "lin", 0, 0), function(param) return round_form(param:get(), 1, "dB") end)
    params:set_action("nb_smpkit_eq_amp_"..i, function(val) set_param(i, 'eqAmp', val) end)

    params:add_control("nb_smpkit_hpf_hz_"..i, "hpf cutoff", controlspec.new(20, 8000, "exp", 0, 20), function(param) return round_form(param:get(), 1, "hz") end)
    params:set_action("nb_smpkit_hpf_hz_"..i, function(val) set_param(i, 'hpfHz', val) end)

    params:add_control("nb_smpkit_hpf_rz_"..i, "hpf resonance", controlspec.new(0, 1, "lin", 0, 0.2), function(param) return round_form(param:get() * 100, 1, "%") end)
    params:set_action("nb_smpkit_hpf_rz_"..i, function(val) set_param(i, 'hpfRz', val) end)
  end
end


--------------------------- nb player ---------------------------

function add_smpkit_player()
  local player = {clk = nil}

  function player:describe()
    return {
      name = "smpkit",
      supports_bend = false,
      supports_slew = false
    }
  end

  function player:active()
    if self.name ~= nil then
      if self.clk ~= nil then
        clock.cancel(self.clk)
      end
      self.clk = clock.run(function()
        clock.sleep(0.4)
        if not is_active then
          is_active = true
          alloc_buffers()
          params:show("nb_smpkit_group")
          _menu.rebuild_params()
        end
      end)
    end
  end

  function player:inactive()
    if self.name ~= nil then
      if self.clk ~= nil then
        clock.cancel(self.clk)
      end
      self.clk = clock.run(function()
        clock.sleep(0.4)
        if is_active then
          is_active = false
          dont_panic()
          free_buffers()
          params:hide("nb_smpkit_group")
          _menu.rebuild_params()
        end
      end)
    end
  end

  function player:stop_all()
    dont_panic()
  end

  function player:set_slew(s)
  end

  function player:pitch_bend(note, amount)
  end

  function player:modulate_note(note, key, value)
  end

  function player:modulate(val)
  end

  function player:note_on(note, vel)
    local vox = ((note - root_note) % NUM_VOICES)
    local oct = track_oct and (note - root_note - vox) / 12 or 0
    local i = vox + 1
    if is_playing[i] and play_mode[i] == 3 then
      stop_voice(vox)
      is_playing[i] = false
    else
      trig_voice(vox, vel, oct)
      is_playing[i] = true
    end
  end

  function player:note_off(note)
    local vox = ((note - root_note) % NUM_VOICES)
    local i = vox + 1
    if play_mode[i] ~= 3 then
      stop_voice(vox)
      is_playing[i] = false
    end
  end

  function player:add_params()
    add_smpkit_params()
  end

  if note_players == nil then
    note_players = {}
  end
  note_players["smpkit"] = player

end


--------------------------- mod zone ---------------------------

local function smpkit_post_system()
  if util.file_exists(preset_path) == false then
    util.make_dir(preset_path)
  end
end

local function smpkit_pre_init()
  init_smpkit()
  add_smpkit_player()
end

local function smpkit_cleanup()
  free_buffers()
end

md.hook.register("system_post_startup", "nb_smpkit post startup", smpkit_post_system)
md.hook.register("script_pre_init", "nb_smpkit pre init", smpkit_pre_init)
md.hook.register("script_post_cleanup", "nb_smpkit cleanup", smpkit_cleanup)
