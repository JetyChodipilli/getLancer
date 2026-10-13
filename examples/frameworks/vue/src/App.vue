<script setup>
import {ref} from 'vue';
const name=ref(''),error=ref(''),saved=ref(''),saves=ref(0);
function save(){const title=name.value.trim();if(title.length<3||title.length>60){error.value='Enter a project name with 3–60 characters.';return;}error.value='';saved.value=title;saves.value++;}
</script>
<template>
 <main><p class="kicker">Vue · reactive form</p><h1>Make the next project clear.</h1><p>An original project brief component. Saved state stays inside this preview.</p>
 <section class="panel" aria-labelledby="brief-heading"><h2 id="brief-heading">Project brief</h2><form @submit.prevent="save" novalidate><div class="row"><label for="project-name">Project name</label></div><input id="project-name" v-model="name" @keydown.enter.prevent="save" maxlength="60" :aria-invalid="Boolean(error)" aria-describedby="project-hint project-error" autocomplete="off"><p id="project-hint" class="meta">3–60 characters. Use synthetic project details.</p><p v-if="error" id="project-error" class="error" role="alert">{{error}}</p><div class="row"><button class="primary" type="button" @click="save">Save brief</button><button type="button" @click="name='';saved='';error='';saves=0">Reset brief</button></div></form><output aria-live="polite">{{saved?'Brief saved: '+saved+' · '+saves+' local save'+(saves===1?'':'s'):'No brief saved yet.'}}</output></section></main>
</template>
